package org.apache.lucene.store;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Simple standalone tool that forever acquires & releases a
 * lock using a specific LockFactory.  Run without any args
 * to see usage.
 *
 * @see VerifyingLockFactory
 * @see LockVerifyServer
 */ 

public class LockStressTest {

  public static void main(String[] args) throws Exception {

    if (args.length != 7) {
      System.out.println("Usage: java org.apache.lucene.store.LockStressTest myID verifierHost verifierPort lockFactoryClassName lockDirName sleepTimeMS count\n" +
                         "\n" +
                         "  myID = int from 0 .. 255 (should be unique for test process)\n" +
                         "  verifierHost = hostname that LockVerifyServer is listening on\n" +
                         "  verifierPort = port that LockVerifyServer is listening on\n" +
                         "  lockFactoryClassName = primary FSLockFactory class that we will use\n" +
                         "  lockDirName = path to the lock directory\n" +
                         "  sleepTimeMS = milliseconds to pause betweeen each lock obtain/release\n" +
                         "  count = number of locking tries\n" +
                         "\n" +
                         "You should run multiple instances of this process, each with its own\n" +
                         "unique ID, and each pointing to the same lock directory, to verify\n" +
                         "that locking is working correctly.\n" +
                         "\n" +
                         "Make sure you are first running LockVerifyServer.");
      System.exit(1);
    }

    int arg = 0;
    final int myID = Integer.parseInt(args[arg++]);

    if (myID < 0 || myID > 255) {
      System.out.println("myID must be a unique int 0..255");
      System.exit(1);
    }

    final String verifierHost = args[arg++];
    final int verifierPort = Integer.parseInt(args[arg++]);
    final String lockFactoryClassName = args[arg++];
    final Path lockDirPath = Paths.get(args[arg++]);
    final int sleepTimeMS = Integer.parseInt(args[arg++]);
    final int count = Integer.parseInt(args[arg++]);

    final LockFactory lockFactory = getNewLockFactory(lockFactoryClassName);
    // we test the lock factory directly, so we don't need it on the directory itsself (the directory is just for testing)
    final FSDirectory lockDir = new SimpleFSDirectory(lockDirPath, NoLockFactory.INSTANCE);
    final InetSocketAddress addr = new InetSocketAddress(verifierHost, verifierPort);
    System.out.println("Connecting to server " + addr +
        " and registering as client " + myID + "...");
    try (Socket socket = new Socket()) {
      socket.setReuseAddress(true);
      socket.connect(addr, 500);
      final OutputStream out = socket.getOutputStream();
      final InputStream in = socket.getInputStream();
      
      out.write(myID);
      out.flush();
      LockFactory verifyLF = new VerifyingLockFactory(lockFactory, in, out);
      Lock l = verifyLF.makeLock(lockDir, "test.lock");
      final Random rnd = new Random();
      
      // wait for starting gun
      if (in.read() != 43) {
        throw new IOException("Protocol violation");
      }
      
      for (int i = 0; i < count; i++) {
        boolean obtained = false;
        try {
          obtained = l.obtain(rnd.nextInt(100) + 10);
        } catch (LockObtainFailedException e) {}
        
        if (obtained) {
          if (rnd.nextInt(10) == 0) {
            if (rnd.nextBoolean()) {
              verifyLF = new VerifyingLockFactory(getNewLockFactory(lockFactoryClassName), in, out);
            }
            final Lock secondLock = verifyLF.makeLock(lockDir, "test.lock");
            if (secondLock.obtain()) {
              throw new IOException("Double Obtain");
            }
          }
          Thread.sleep(sleepTimeMS);
          l.close();
        }
        
        if (i % 500 == 0) {
          System.out.println((i * 100. / count) + "% done.");
        }
        
        Thread.sleep(sleepTimeMS);
      } 
    }
    
    System.out.println("Finished " + count + " tries.");
  }


  private static FSLockFactory getNewLockFactory(String lockFactoryClassName) throws IOException {
    // try to get static INSTANCE field of class
    try {
      return (FSLockFactory) Class.forName(lockFactoryClassName).getField("INSTANCE").get(null);
    } catch (ReflectiveOperationException e) {
      // fall-through
    }
    
    // try to create a new instance
    try {
      return Class.forName(lockFactoryClassName).asSubclass(FSLockFactory.class).newInstance();
    } catch (IllegalAccessException | InstantiationException | ClassCastException | ClassNotFoundException e) {
      // fall-through
    }

    throw new IOException("Cannot get lock factory singleton of " + lockFactoryClassName);
  }
}

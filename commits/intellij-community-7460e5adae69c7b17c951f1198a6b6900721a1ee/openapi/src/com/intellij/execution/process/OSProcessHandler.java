/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;

import java.io.*;
import java.nio.charset.Charset;

public class OSProcessHandler extends ProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.OSProcessHandler");
  private final Process myProcess;
  private final String myCommandLine;

  private final ProcessWaitFor myWaitFor;

  public OSProcessHandler(final Process process, final String commandLine) {
    myProcess = process;
    myCommandLine = commandLine;
    myWaitFor = new ProcessWaitFor(process);
  }

  private static class ProcessWaitFor  {
    private final Semaphore myWaitSemaphore = new Semaphore();

    private final Thread myWaitForThread;
    private int myExitCode;

    public void detach() {
      myWaitForThread.interrupt();
      myWaitSemaphore.up();
    }

    public ProcessWaitFor(final Process process) {
      myWaitSemaphore.down();
      myWaitForThread = new Thread() {
        public void run() {
          try {
            myExitCode = process.waitFor();
          }
          catch (InterruptedException e) {
          }
          myWaitSemaphore.up();
        }
      };
      myWaitForThread.start();
    }

    public int waitFor() {
      myWaitSemaphore.waitFor();
      return myExitCode;
    }
  }

  public Process getProcess() {
    return myProcess;
  }

  public void startNotify() {
    final ReadProcessThread stdoutThread = new ReadProcessThread(createProcessOutReader()) {
      protected void textAvailable(String s) {
        notifyTextAvailable(s, ProcessOutputTypes.STDOUT);
      }
    };

    final ReadProcessThread stderrThread = new ReadProcessThread(createProcessErrReader()) {
      protected void textAvailable(String s) {
        notifyTextAvailable(s, ProcessOutputTypes.STDERR);
      }
    };

    notifyTextAvailable(myCommandLine + '\n', ProcessOutputTypes.SYSTEM);

    addProcessListener(new ProcessAdapter() {
      public void startNotified(final ProcessEvent event) {
        try {
          stdoutThread.start();
          stderrThread.start();

          new Thread() {
            public void run() {
              int exitCode = 0;

              try {
                exitCode = myWaitFor.waitFor();

                stderrThread.join(0);
                stdoutThread.join(0);
              }
              catch (InterruptedException e) {
              }

              notifyProcessTerminated(exitCode);
            }
          }.start();
        }
        finally {
          removeProcessListener(this);
        }
      }
    });

    super.startNotify();
  }

  protected Reader createProcessOutReader() {
    return new BufferedReader(new InputStreamReader(myProcess.getInputStream(), getCharset()));
  }

  protected Reader createProcessErrReader() {
    return new BufferedReader(new InputStreamReader(myProcess.getErrorStream(), getCharset()));
  }

  protected void destroyProcessImpl() {
    try {
      myProcess.getOutputStream().close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      myProcess.destroy();
    }
  }

  protected void detachProcessImpl() {
    new Thread(new Runnable() {
      public void run() {
        try {
          myProcess.getOutputStream().close();
        }
        catch (IOException e) {
          LOG.error(e);
        }

        myWaitFor.detach();
      }
    }).start();
  }

  public boolean detachIsDefault() {
    return false;
  }

  public OutputStream getProcessInput() {
    return myProcess.getOutputStream();
  }

  // todo: to remove
  public String getCommandLine() {
    return myCommandLine;
  }


  public Charset getCharset() {
    return CharsetToolkit.getIDEOptionsCharset();
  }

  private static abstract class ReadProcessThread extends Thread {
    private static final int NOTIFY_TEXT_DELAY = 300;

    private final Reader myReader;

    private final StringBuffer myBuffer = new StringBuffer();
    private final Alarm myAlarm;

    private boolean myIsClosed = false;

    public ReadProcessThread(final Reader reader) {
      LOG.assertTrue(reader != null);
      setPriority(Thread.MAX_PRIORITY);
      myReader = reader;
      myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    }

    public void run() {
      myAlarm.addRequest(new Runnable() {
        public void run() {
          if(myIsClosed) return;

          myAlarm.addRequest(this, NOTIFY_TEXT_DELAY);
          checkTextAvailable();
        }
      }, NOTIFY_TEXT_DELAY);

      try {
        while (true) {
          final int c = readNextByte();
          if (c == -1) {
            break;
          }
          synchronized (myBuffer) {
            myBuffer.append((char)c);
          }
          if (c == '\n') { // not by '\r' because of possible '\n'
            checkTextAvailable();
          }
        }
      }
      catch (Exception e) {
        LOG.error(e);
        e.printStackTrace();
      }

      close();
    }

    private int readNextByte() {
      try {
        return myReader.read();
      }
      catch (IOException e) {
        return -1; // When process terminated Process.getInputStream()'s underlaying stream becomes closed on Linux.
      }
    }

    private void checkTextAvailable() {
      synchronized (myBuffer) {
        if (myBuffer.length() == 0) return;
        // warning! Since myBuffer is reused, do not use myBuffer.toString() to fetch the string
        // because the created string will get StringBuffer's internal char array as a buffer which is possibly too large.
        final String s = myBuffer.substring(0, myBuffer.length());
        myBuffer.setLength(0);
        textAvailable(s);
      }
    }

    public void close() {
      flushAll();
      try {
        if(Thread.currentThread() != this) join(0);
      }
      catch (InterruptedException e) {
      }
    }

    private void flushAll() {
      myIsClosed = true;
      try {
        myReader.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      checkTextAvailable();
    }

    protected abstract void textAvailable(final String s);
  }
}

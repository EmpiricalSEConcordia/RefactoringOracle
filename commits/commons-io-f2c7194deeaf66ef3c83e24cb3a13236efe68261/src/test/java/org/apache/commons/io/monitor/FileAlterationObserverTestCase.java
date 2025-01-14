/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.io.monitor;

import java.io.File;
import java.io.FileFilter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;

/**
 * {@link FileAlterationObserver} Test Case.
 */
public class FileAlterationObserverTestCase extends AbstractMonitorTestCase {

    /**
     * Construct a new test case.
     *
     * @param name The name of the test
     */
    public FileAlterationObserverTestCase(String name) {
        super(name);
        testDirName = "test-observer";
    }

    @Override
    protected void setUp() throws Exception {
        listener = new CollectionFileListener(true);
        super.setUp();
    }

    /**
     * Test checkAndNotify() method
     */
    public void testDirectory() {
        try {
            checkAndNotify();
            checkCollectionsEmpty("A");
            File testDirA = new File(testDir, "test-dir-A");
            File testDirB = new File(testDir, "test-dir-B");
            File testDirC = new File(testDir, "test-dir-C");
            testDirA.mkdir();
            testDirB.mkdir();
            testDirC.mkdir();
            File testDirAFile1 = touch(new File(testDirA, "A-file1.java"));
            File testDirAFile2 = touch(new File(testDirA, "A-file2.txt")); // filter should ignore this
            File testDirAFile3 = touch(new File(testDirA, "A-file3.java"));
            File testDirAFile4 = touch(new File(testDirA, "A-file4.java"));
            File testDirBFile1 = touch(new File(testDirB, "B-file1.java"));
 
            checkAndNotify();
            checkCollectionSizes("B", 3, 0, 0, 4, 0, 0);
            assertTrue("B testDirA",   listener.getCreatedDirectories().contains(testDirA));
            assertTrue("B testDirB",   listener.getCreatedDirectories().contains(testDirB));
            assertTrue("B testDirC",   listener.getCreatedDirectories().contains(testDirC));
            assertTrue("B testDirAFile1", listener.getCreatedFiles().contains(testDirAFile1));
            assertFalse("B testDirAFile2", listener.getCreatedFiles().contains(testDirAFile2));
            assertTrue("B testDirAFile3", listener.getCreatedFiles().contains(testDirAFile3));
            assertTrue("B testDirAFile4", listener.getCreatedFiles().contains(testDirAFile4));
            assertTrue("B testDirBFile1", listener.getCreatedFiles().contains(testDirBFile1));

            checkAndNotify();
            checkCollectionsEmpty("C");

            testDirAFile4 = touch(testDirAFile4);
            FileUtils.deleteDirectory(testDirB);
            checkAndNotify();
            checkCollectionSizes("D", 0, 0, 1, 0, 1, 1);
            assertTrue("D testDirB",   listener.getDeletedDirectories().contains(testDirB));
            assertTrue("D testDirAFile4", listener.getChangedFiles().contains(testDirAFile4));
            assertTrue("D testDirBFile1", listener.getDeletedFiles().contains(testDirBFile1));

            FileUtils.deleteDirectory(testDir);
            checkAndNotify();
            checkCollectionSizes("E", 0, 0, 2, 0, 0, 3);
            assertTrue("E testDirA",   listener.getDeletedDirectories().contains(testDirA));
            assertTrue("E testDirAFile1", listener.getDeletedFiles().contains(testDirAFile1));
            assertFalse("E testDirAFile2", listener.getDeletedFiles().contains(testDirAFile2));
            assertTrue("E testDirAFile3", listener.getDeletedFiles().contains(testDirAFile3));
            assertTrue("E testDirAFile4", listener.getDeletedFiles().contains(testDirAFile4));
            
            testDir.mkdir();
            checkAndNotify();
            checkCollectionsEmpty("F");

            checkAndNotify();
            checkCollectionsEmpty("G");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Threw " + e);
        }
    }

    /**
     * Test checkAndNotify() creating
     */
    public void testFileCreate() {
        try {
            checkAndNotify();
            checkCollectionsEmpty("A");
            File testDirA = new File(testDir, "test-dir-A");
            testDirA.mkdir();
            testDir  = touch(testDir);
            testDirA = touch(testDirA);
            File testDirAFile1 =       new File(testDirA, "A-file1.java");
            File testDirAFile2 = touch(new File(testDirA, "A-file2.java"));
            File testDirAFile3 =       new File(testDirA, "A-file3.java");
            File testDirAFile4 = touch(new File(testDirA, "A-file4.java"));
            File testDirAFile5 =       new File(testDirA, "A-file5.java");
 
            checkAndNotify();
            checkCollectionSizes("B", 1, 0, 0, 2, 0, 0);
            assertFalse("B testDirAFile1", listener.getCreatedFiles().contains(testDirAFile1));
            assertTrue("B testDirAFile2",  listener.getCreatedFiles().contains(testDirAFile2));
            assertFalse("B testDirAFile3", listener.getCreatedFiles().contains(testDirAFile3));
            assertTrue("B testDirAFile4",  listener.getCreatedFiles().contains(testDirAFile4));
            assertFalse("B testDirAFile5", listener.getCreatedFiles().contains(testDirAFile5));

            assertFalse("B testDirAFile1 exists", testDirAFile1.exists());
            assertTrue("B testDirAFile2 exists",  testDirAFile2.exists());
            assertFalse("B testDirAFile3 exists", testDirAFile3.exists());
            assertTrue("B testDirAFile4 exists",  testDirAFile4.exists());
            assertFalse("B testDirAFile5 exists", testDirAFile5.exists());

            checkAndNotify();
            checkCollectionsEmpty("C");

            // Create file with name < first entry
            testDirAFile1 = touch(testDirAFile1);
            testDirA      = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("D", 0, 1, 0, 1, 0, 0);
            assertTrue("D testDirAFile1 exists", testDirAFile1.exists());
            assertTrue("D testDirAFile1",  listener.getCreatedFiles().contains(testDirAFile1));

            // Create file with name between 2 entries
            testDirAFile3 = touch(testDirAFile3);
            testDirA      = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("E", 0, 1, 0, 1, 0, 0);
            assertTrue("E testDirAFile3 exists", testDirAFile3.exists());
            assertTrue("E testDirAFile3",  listener.getCreatedFiles().contains(testDirAFile3));

            // Create file with name > last entry
            testDirAFile5 = touch(testDirAFile5);
            testDirA      = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("F", 0, 1, 0, 1, 0, 0);
            assertTrue("F testDirAFile5 exists", testDirAFile5.exists());
            assertTrue("F testDirAFile5",  listener.getCreatedFiles().contains(testDirAFile5));
        } catch (Exception e) {
            fail("Threw " + e);
        }
    }

    /**
     * Test checkAndNotify() creating
     */
    public void testFileUpdate() {
        try {
            checkAndNotify();
            checkCollectionsEmpty("A");
            File testDirA = new File(testDir, "test-dir-A");
            testDirA.mkdir();
            testDir  = touch(testDir);
            testDirA = touch(testDirA);
            File testDirAFile1 = touch(new File(testDirA, "A-file1.java"));
            File testDirAFile2 = touch(new File(testDirA, "A-file2.java"));
            File testDirAFile3 = touch(new File(testDirA, "A-file3.java"));
            File testDirAFile4 = touch(new File(testDirA, "A-file4.java"));
            File testDirAFile5 = touch(new File(testDirA, "A-file5.java"));
 
            checkAndNotify();
            checkCollectionSizes("B", 1, 0, 0, 5, 0, 0);
            assertTrue("B testDirAFile1", listener.getCreatedFiles().contains(testDirAFile1));
            assertTrue("B testDirAFile2", listener.getCreatedFiles().contains(testDirAFile2));
            assertTrue("B testDirAFile3", listener.getCreatedFiles().contains(testDirAFile3));
            assertTrue("B testDirAFile4", listener.getCreatedFiles().contains(testDirAFile4));
            assertTrue("B testDirAFile5", listener.getCreatedFiles().contains(testDirAFile5));

            assertTrue("B testDirAFile1 exists", testDirAFile1.exists());
            assertTrue("B testDirAFile2 exists", testDirAFile2.exists());
            assertTrue("B testDirAFile3 exists", testDirAFile3.exists());
            assertTrue("B testDirAFile4 exists", testDirAFile4.exists());
            assertTrue("B testDirAFile5 exists", testDirAFile5.exists());

            checkAndNotify();
            checkCollectionsEmpty("C");

            // Update first entry
            testDirAFile1 = touch(testDirAFile1);
            testDirA      = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("D", 0, 1, 0, 0, 1, 0);
            assertTrue("D testDirAFile1",  listener.getChangedFiles().contains(testDirAFile1));

            // Update file with name between 2 entries
            testDirAFile3 = touch(testDirAFile3);
            testDirA      = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("E", 0, 1, 0, 0, 1, 0);
            assertTrue("E testDirAFile3",  listener.getChangedFiles().contains(testDirAFile3));

            // Update last entry
            testDirAFile5 = touch(testDirAFile5);
            testDirA      = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("F", 0, 1, 0, 0, 1, 0);
            assertTrue("F testDirAFile5",  listener.getChangedFiles().contains(testDirAFile5));
        } catch (Exception e) {
            fail("Threw " + e);
        }
    }

    /**
     * Test checkAndNotify() deleting
     */
    public void testFileDelete() {
        try {
            checkAndNotify();
            checkCollectionsEmpty("A");
            File testDirA = new File(testDir, "test-dir-A");
            testDirA.mkdir();
            testDir  = touch(testDir);
            testDirA = touch(testDirA);
            File testDirAFile1 = touch(new File(testDirA, "A-file1.java"));
            File testDirAFile2 = touch(new File(testDirA, "A-file2.java"));
            File testDirAFile3 = touch(new File(testDirA, "A-file3.java"));
            File testDirAFile4 = touch(new File(testDirA, "A-file4.java"));
            File testDirAFile5 = touch(new File(testDirA, "A-file5.java"));

            assertTrue("B testDirAFile1 exists", testDirAFile1.exists());
            assertTrue("B testDirAFile2 exists", testDirAFile2.exists());
            assertTrue("B testDirAFile3 exists", testDirAFile3.exists());
            assertTrue("B testDirAFile4 exists", testDirAFile4.exists());
            assertTrue("B testDirAFile5 exists", testDirAFile5.exists());

            checkAndNotify();
            checkCollectionSizes("B", 1, 0, 0, 5, 0, 0);
            assertTrue("B testDirAFile1", listener.getCreatedFiles().contains(testDirAFile1));
            assertTrue("B testDirAFile2", listener.getCreatedFiles().contains(testDirAFile2));
            assertTrue("B testDirAFile3", listener.getCreatedFiles().contains(testDirAFile3));
            assertTrue("B testDirAFile4", listener.getCreatedFiles().contains(testDirAFile4));
            assertTrue("B testDirAFile5", listener.getCreatedFiles().contains(testDirAFile5));

            checkAndNotify();
            checkCollectionsEmpty("C");

            // Delete first entry
            FileUtils.deleteQuietly(testDirAFile1);
            testDirA = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("D", 0, 1, 0, 0, 0, 1);
            assertFalse("D testDirAFile1 exists", testDirAFile1.exists());
            assertTrue("D testDirAFile1",  listener.getDeletedFiles().contains(testDirAFile1));

            // Delete file with name between 2 entries
            FileUtils.deleteQuietly(testDirAFile3);
            testDirA = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("E", 0, 1, 0, 0, 0, 1);
            assertFalse("E testDirAFile3 exists", testDirAFile3.exists());
            assertTrue("E testDirAFile3",  listener.getDeletedFiles().contains(testDirAFile3));

            // Delete last entry
            FileUtils.deleteQuietly(testDirAFile5);
            testDirA = touch(testDirA);
            checkAndNotify();
            checkCollectionSizes("F", 0, 1, 0, 0, 0, 1);
            assertFalse("F testDirAFile5 exists", testDirAFile5.exists());
            assertTrue("F testDirAFile5",  listener.getDeletedFiles().contains(testDirAFile5));

        } catch (Exception e) {
            fail("Threw " + e);
        }
    }

    /**
     * Test checkAndNotify() method
     */
    public void testObserveSingleFile() {
        try {
            File testDirA = new File(testDir, "test-dir-A");
            File testDirAFile1 = new File(testDirA, "A-file1.java");
            testDirA.mkdir();

            FileFilter nameFilter = FileFilterUtils.nameFileFilter(testDirAFile1.getName());
            createObserver(testDirA, nameFilter);
            checkAndNotify();
            checkCollectionsEmpty("A");
            assertFalse("A testDirAFile1 exists", testDirAFile1.exists());

            // Create
            testDirAFile1 = touch(testDirAFile1);
            File testDirAFile2 = touch(new File(testDirA, "A-file2.txt"));  /* filter should ignore */
            File testDirAFile3 = touch(new File(testDirA, "A-file3.java")); /* filter should ignore */
            assertTrue("B testDirAFile1 exists", testDirAFile1.exists());
            assertTrue("B testDirAFile2 exists", testDirAFile2.exists());
            assertTrue("B testDirAFile3 exists", testDirAFile3.exists());
            checkAndNotify();
            checkCollectionSizes("C", 0, 0, 0, 1, 0, 0);
            assertTrue("C created", listener.getCreatedFiles().contains(testDirAFile1));
            assertFalse("C created", listener.getCreatedFiles().contains(testDirAFile2));
            assertFalse("C created", listener.getCreatedFiles().contains(testDirAFile3));

            // Modify
            testDirAFile1 = touch(testDirAFile1);
            testDirAFile2 = touch(testDirAFile2);
            testDirAFile3 = touch(testDirAFile3);
            checkAndNotify();
            checkCollectionSizes("D", 0, 0, 0, 0, 1, 0);
            assertTrue("D changed", listener.getChangedFiles().contains(testDirAFile1));
            assertFalse("D changed", listener.getChangedFiles().contains(testDirAFile2));
            assertFalse("D changed", listener.getChangedFiles().contains(testDirAFile3));

            // Delete
            FileUtils.deleteQuietly(testDirAFile1);
            FileUtils.deleteQuietly(testDirAFile2);
            FileUtils.deleteQuietly(testDirAFile3);
            assertFalse("E testDirAFile1 exists", testDirAFile1.exists());
            assertFalse("E testDirAFile2 exists", testDirAFile2.exists());
            assertFalse("E testDirAFile3 exists", testDirAFile3.exists());
            checkAndNotify();
            checkCollectionSizes("E", 0, 0, 0, 0, 0, 1);
            assertTrue("E deleted", listener.getDeletedFiles().contains(testDirAFile1));
            assertFalse("E deleted", listener.getDeletedFiles().contains(testDirAFile2));
            assertFalse("E deleted", listener.getDeletedFiles().contains(testDirAFile3));

        } catch (Exception e) {
            fail("Threw " + e);
        }
    }

    /**
     * Call {@link FileAlterationObserver#checkAndNotify()}.
     *
     * @throws Exception if an error occurs
     */
    protected void checkAndNotify() throws Exception {
        observer.checkAndNotify();
    }
}

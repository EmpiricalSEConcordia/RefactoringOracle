/*
 * Copyright 2005-2006 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.io.filefilter;

import java.io.File;

/**
 * This filter accepts <code>File</code>s that are hidden.
 * <p>
 * Example, showing how to print out a list of the
 * current directory's <i>hidden</i> files:
 *
 * <pre>
 * File dir = new File(".");
 * String[] files = dir.list( HiddenFileFilter.HIDDEN );
 * for ( int i = 0; i &lt; files.length; i++ ) {
 *     System.out.println(files[i]);
 * }
 * </pre>
 *
 * <p>
 * Example, showing how to print out a list of the
 * current directory's <i>visible</i> (i.e. not hidden) files:
 *
 * <pre>
 * File dir = new File(".");
 * String[] files = dir.list( HiddenFileFilter.VISIBLE );
 * for ( int i = 0; i &lt; files.length; i++ ) {
 *     System.out.println(files[i]);
 * }
 * </pre>
 *
 * @since Commons IO 1.3
 * @version $Revision$
 */
public class HiddenFileFilter extends AbstractFileFilter {
    
    /** Singleton instance of <i>hidden</i> filter */
    public static final IOFileFilter HIDDEN  = new HiddenFileFilter();
    
    /** Singleton instance of <i>visible</i> filter */
    public static final IOFileFilter VISIBLE = new NotFileFilter(HIDDEN);
    
    /**
     * Restrictive consructor.
     */
    protected HiddenFileFilter() {
    }
    
    /**
     * Checks to see if the file is hidden.
     * 
     * @param file  the File to check
     * @return <code>true</code> if the file is
     *  <i>hidden</i>, otherwise <code>false</code>.
     */
    public boolean accept(File file) {
        return file.isHidden();
    }
    
}

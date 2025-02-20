/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.gdata.search.index;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Simon Willnauer
 *
 */
public class IndexEventListenerStub implements IndexEventListener {
    AtomicInteger count;
    /**
     * 
     */
    public IndexEventListenerStub() {
        super();
       this.count = new AtomicInteger(0);
    }

    /**
     * @see org.apache.lucene.gdata.search.index.IndexEventListener#commitCallBack(java.lang.String)
     */
    public void commitCallBack(String service) {
        this.count.incrementAndGet();
    }
    
    public int getCalledCount(){
        return this.count.get();
    }

}

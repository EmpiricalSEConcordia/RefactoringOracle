/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.tree.xml;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Sep 9, 2004
 * Time: 8:11:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class IDTDElementType extends IElementType{
  public IDTDElementType(String debugName) {
    super(debugName, StdFileTypes.DTD.getLanguage());
  }
}

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
package com.intellij.compiler.impl.javaCompiler.javac;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Matcher;

import com.intellij.compiler.ParserAction;
import com.intellij.compiler.OutputParser;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 14, 2005
 */
public abstract class JavacParserAction extends ParserAction {
  private final Matcher myMatcher;

  protected JavacParserAction(final Matcher matcher) {
    myMatcher = matcher;
  }

  public final boolean execute(String line, final OutputParser.Callback callback) {
    myMatcher.reset(line);
    if (!myMatcher.matches()) {
      return false;
    }
    final String parsed = myMatcher.groupCount() >= 1 ? myMatcher.group(1).replace(File.separatorChar, '/') : null;
    doExecute(parsed, callback);
    return true;
  }

  protected abstract void doExecute(@Nullable String parsedData, final OutputParser.Callback callback);

}

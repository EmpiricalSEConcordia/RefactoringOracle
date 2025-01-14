/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.options.colors.pages;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.OptionsBundle;

import javax.swing.*;
import java.util.Map;

public class PropertiesColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor(OptionsBundle.message("options.properties.attribute.descriptor.property.key"), PropertiesHighlighter.PROPERTY_KEY),
    new AttributesDescriptor(OptionsBundle.message("options.properties.attribute.descriptor.property.value"), PropertiesHighlighter.PROPERTY_VALUE),
    new AttributesDescriptor(OptionsBundle.message("options.properties.attribute.descriptor.key.value.separator"), PropertiesHighlighter.PROPERTY_KEY_VALUE_SEPARATOR),
    new AttributesDescriptor(OptionsBundle.message("options.properties.attribute.descriptor.comment"), PropertiesHighlighter.PROPERTY_COMMENT),
    new AttributesDescriptor(OptionsBundle.message("options.properties.attribute.descriptor.valid.string.escape"), PropertiesHighlighter.PROPERTIES_VALID_STRING_ESCAPE),
    new AttributesDescriptor(OptionsBundle.message("options.properties.attribute.descriptor.invalid.string.escape"), PropertiesHighlighter.PROPERTIES_INVALID_STRING_ESCAPE),
  };

  private static final ColorDescriptor[] COLORS = new ColorDescriptor[0];

  public String getDisplayName() {
    return OptionsBundle.message("properties.options.display.name");
  }

  public Icon getIcon() {
    return PropertiesFileType.FILE_ICON;
  }

  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  public ColorDescriptor[] getColorDescriptors() {
    return COLORS;
  }

  public SyntaxHighlighter getHighlighter() {
    return new PropertiesHighlighter();
  }

  public String getDemoText() {
    return "# Comment on keys and values\n" +
           "key1=value1\n" +
           "! other values:\n" +
           "a\\=\\fb : x\\ty\\n\\x\\uzzzz\n"
      ;
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}
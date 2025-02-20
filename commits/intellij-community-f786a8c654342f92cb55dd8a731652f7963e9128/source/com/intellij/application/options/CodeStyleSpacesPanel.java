package com.intellij.application.options;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class CodeStyleSpacesPanel extends OptionTreeWithPreviewPanel {
  public CodeStyleSpacesPanel(CodeStyleSettings settings) {
    super(settings);
  }

  private static final String AROUND_OPERATORS = ApplicationBundle.message("group.spaces.around.operators");
  private static final String BEFORE_PARENTHESES = ApplicationBundle.message("group.spaces.before.parentheses");
  private static final String BEFORE_LEFT_BRACE = ApplicationBundle.message("group.spaces.before.left.brace");
  private static final String WITHIN_PARENTHESES = ApplicationBundle.message("group.spaces.within.parentheses");
  private static final String TERNARY_OPERATOR = ApplicationBundle.message("group.spaces.in.ternary.operator");
  private static final String OTHER = ApplicationBundle.message("group.spaces.other");

  protected void initTables() {
    initBooleanField("SPACE_BEFORE_METHOD_CALL_PARENTHESES", ApplicationBundle.message("checkbox.spaces.method.call.parentheses"), BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_METHOD_PARENTHESES", ApplicationBundle.message("checkbox.spaces.method.declaration.parentheses"), BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_IF_PARENTHESES", ApplicationBundle.message("checkbox.spaces.if.parentheses"), BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_WHILE_PARENTHESES", ApplicationBundle.message("checkbox.spaces.while.parentheses"), BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_FOR_PARENTHESES", ApplicationBundle.message("checkbox.spaces.for.parentheses"), BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_CATCH_PARENTHESES", ApplicationBundle.message("checkbox.spaces.catch.parentheses"), BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_SWITCH_PARENTHESES", ApplicationBundle.message("checkbox.spaces.switch.parentheses"), BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_SYNCHRONIZED_PARENTHESES", ApplicationBundle.message("checkbox.spaces.synchronized.parentheses"), BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_ANOTATION_PARAMETER_LIST", ApplicationBundle.message("checkbox.spaces.annotation.parameters"), BEFORE_PARENTHESES);

    initBooleanField("SPACE_AROUND_ASSIGNMENT_OPERATORS", ApplicationBundle.message("checkbox.spaces.assignment.operators"), AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_LOGICAL_OPERATORS", ApplicationBundle.message("checkbox.spaces.logical.operators"), AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_EQUALITY_OPERATORS", ApplicationBundle.message("checkbox.spaces.equality.operators"), AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_RELATIONAL_OPERATORS", ApplicationBundle.message("checkbox.spaces.relational.operators"), AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_BITWISE_OPERATORS", ApplicationBundle.message("checkbox.spaces.bitwise.operators"), AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_ADDITIVE_OPERATORS", ApplicationBundle.message("checkbox.spaces.additive.operators"), AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_MULTIPLICATIVE_OPERATORS", ApplicationBundle.message("checkbox.spaces.multiplicative.operators"), AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_SHIFT_OPERATORS", ApplicationBundle.message("checkbox.spaces.shift.operators"), AROUND_OPERATORS);

    initBooleanField("SPACE_BEFORE_CLASS_LBRACE", ApplicationBundle.message("checkbox.spaces.class.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_METHOD_LBRACE", ApplicationBundle.message("checkbox.spaces.method.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_IF_LBRACE", ApplicationBundle.message("checkbox.spaces.if.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_ELSE_LBRACE", ApplicationBundle.message("checkbox.spaces.else.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_WHILE_LBRACE", ApplicationBundle.message("checkbox.spaces.while.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_FOR_LBRACE", ApplicationBundle.message("checkbox.spaces.for.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_DO_LBRACE", ApplicationBundle.message("checkbox.spaces.do.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_SWITCH_LBRACE", ApplicationBundle.message("checkbox.spaces.switch.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_TRY_LBRACE", ApplicationBundle.message("checkbox.spaces.try.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_CATCH_LBRACE", ApplicationBundle.message("checkbox.spaces.catch.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_FINALLY_LBRACE", ApplicationBundle.message("checkbox.spaces.finally.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_SYNCHRONIZED_LBRACE", ApplicationBundle.message("checkbox.spaces.synchronized.left.brace"), BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE", ApplicationBundle.message("checkbox.spaces.array.initializer.left.brace"), BEFORE_LEFT_BRACE);

    initBooleanField("SPACE_WITHIN_PARENTHESES", ApplicationBundle.message("checkbox.spaces.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_METHOD_CALL_PARENTHESES", ApplicationBundle.message("checkbox.spaces.checkbox.spaces.method.call.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_METHOD_PARENTHESES", ApplicationBundle.message("checkbox.spaces.checkbox.spaces.method.declaration.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_IF_PARENTHESES", ApplicationBundle.message("checkbox.spaces.if.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_WHILE_PARENTHESES", ApplicationBundle.message("checkbox.spaces.while.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_FOR_PARENTHESES", ApplicationBundle.message("checkbox.spaces.for.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_CATCH_PARENTHESES", ApplicationBundle.message("checkbox.spaces.catch.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_SWITCH_PARENTHESES", ApplicationBundle.message("checkbox.spaces.switch.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_SYNCHRONIZED_PARENTHESES", ApplicationBundle.message("checkbox.spaces.synchronized.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_CAST_PARENTHESES", ApplicationBundle.message("checkbox.spaces.type.cast.parentheses"), WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_ANNOTATION_PARENTHESES", ApplicationBundle.message("checkbox.spaces.annotation.parentheses"), WITHIN_PARENTHESES);

    initBooleanField("SPACE_BEFORE_QUEST", ApplicationBundle.message("checkbox.spaces.before.question"), TERNARY_OPERATOR);
    initBooleanField("SPACE_AFTER_QUEST", ApplicationBundle.message("checkbox.spaces.after.question"), TERNARY_OPERATOR);
    initBooleanField("SPACE_BEFORE_COLON", ApplicationBundle.message("checkbox.spaces.before.colon"), TERNARY_OPERATOR);
    initBooleanField("SPACE_AFTER_COLON", ApplicationBundle.message("checkbox.spaces.after.colon"), TERNARY_OPERATOR);

    initBooleanField("SPACE_AFTER_LABEL", ApplicationBundle.message("checkbox.spaces.after.colon.in.label.declaration"), OTHER);
    initBooleanField("SPACE_WITHIN_BRACKETS", ApplicationBundle.message("checkbox.spaces.within.brackets"), OTHER);
    initBooleanField("SPACE_WITHIN_ARRAY_INITIALIZER_BRACES", ApplicationBundle.message("checkbox.spaces.within.array.initializer.braces"), OTHER);
    initBooleanField("SPACE_AFTER_COMMA", ApplicationBundle.message("checkbox.spaces.after.comma"), OTHER);
    initBooleanField("SPACE_BEFORE_COMMA", ApplicationBundle.message("checkbox.spaces.before.comma"), OTHER);
    initBooleanField("SPACE_AFTER_SEMICOLON", ApplicationBundle.message("checkbox.spaces.after.semicolon"), OTHER);
    initBooleanField("SPACE_BEFORE_SEMICOLON", ApplicationBundle.message("checkbox.spaces.before.semicolon"), OTHER);
    initBooleanField("SPACE_AFTER_TYPE_CAST", ApplicationBundle.message("checkbox.spaces.after.type.cast"), OTHER);
  }

  protected void setupEditorSettings(Editor editor) {
    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setWhitespacesShown(true);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(1);
  }

  protected String getPreviewText() {
    return "@Annotation(param1=\"value1\", param2=\"value2\") public class Foo {\n" +
           "  int[] X = new int[]{1,3,5,6,7,87,1213,2};\n\n" +
           "  public void foo(int x, int y) {\n" +
           "    for(int i = 0; i < x; i++){\n" +
           "      y += (y ^ 0x123) << 2;\n" +
           "    }\n" +
           "    do {\n" +
           "      try {\n" +
           "        if(0 < x && x < 10) {\n" +
           "          while(x != y){\n" +
           "            x = f(x * 3 + 5);\n" +
           "          }\n" +
           "        } else {\n" +
           "          synchronized(this){\n" +
           "            switch(e.getCode()){\n" +
           "              //...\n" +
           "            }\n" +
           "          }\n" +
           "        }\n" +
           "      }\n" +
           "      catch(MyException e) {\n" +
           "      }\n" +
           "      finally {\n" +
           "        int[] arr = (int[])g(y);\n" +
           "        x = y >= 0 ? arr[y] : -1;\n" +
           "      }\n" +
           "    }while(true);\n" +
           "  }\n" +
           "}";
  }

  public JComponent getPanel() {
    return getInternalPanel();
  }
}
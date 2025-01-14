
package com.intellij.refactoring.anonymousToInner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

class AnonymousToInnerDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.anonymousToInner.AnonymousToInnerDialog");

  private final Project myProject;
  private final PsiAnonymousClass myAnonClass;
  private final boolean myNeedsThis;

  private EditorTextField myNameField;
  private final ParameterTablePanel.VariableData[] myVariableData;
  private Map<PsiVariable,VariableInfo> myVariableToInfoMap = new HashMap<PsiVariable, VariableInfo>();
  private JCheckBox myCbMakeStatic;

  public AnonymousToInnerDialog(Project project, PsiAnonymousClass anonClass, final VariableInfo[] variableInfos,
                                boolean needsThis) {
    super(project, true);
    myProject = project;
    myAnonClass = anonClass;
    myNeedsThis = needsThis;

    setTitle(AnonymousToInnerHandler.REFACTORING_NAME);

    for (VariableInfo info : variableInfos) {
      myVariableToInfoMap.put(info.variable, info);
    }
    myVariableData = new ParameterTablePanel.VariableData[variableInfos.length];

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
    for(int idx = 0; idx < variableInfos.length; idx++){
      VariableInfo info = variableInfos[idx];
      String name = info.variable.getName();
      VariableKind kind = codeStyleManager.getVariableKind(info.variable);
      name = codeStyleManager.variableNameToPropertyName(name, kind);
      name = codeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
      ParameterTablePanel.VariableData data = new ParameterTablePanel.VariableData(info.variable);
      data.name = name;
      data.passAsParameter = true;
      myVariableData[idx] = data;
    }

    init();

    String name = myAnonClass.getBaseClassReference().getReferenceName();
    @NonNls final String prefix = "My";
    name = prefix + name; //?
    myNameField.setText(name);
    myNameField.selectAll();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  public boolean isMakeStatic() {
    if(myNeedsThis) {
      return false;
    }
    else {
      return myCbMakeStatic.isSelected();
    }
  }

  public String getClassName() {
    return myNameField.getText().trim();
  }

  public VariableInfo[] getVariableInfos() {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
    VariableInfo[] infos = new VariableInfo[myVariableData.length];
    for (int idx = 0; idx < myVariableData.length; idx++) {
      ParameterTablePanel.VariableData data = myVariableData[idx];
      VariableInfo info = myVariableToInfoMap.get(data.variable);

      info.passAsParameter = data.passAsParameter;
      info.parameterName = data.name;
      info.parameterName = data.name;
      String propertyName = codeStyleManager.variableNameToPropertyName(data.name, VariableKind.PARAMETER);
      info.fieldName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.FIELD);

      infos[idx] = info;
    }
    return infos;
  }

  protected void doOKAction(){
    String errorString = null;
    final String innerClassName = getClassName();
    final PsiManager manager = PsiManager.getInstance(myProject);
    if ("".equals(innerClassName)) {
      errorString = RefactoringBundle.message("anonymousToInner.no.inner.class.name");
    }
    else if (!manager.getNameHelper().isIdentifier(innerClassName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(innerClassName);
    }
    else{
      PsiElement targetContainer = AnonymousToInnerHandler.findTargetContainer(myAnonClass);
      if (targetContainer instanceof PsiClass) {
        PsiClass targetClass = (PsiClass)targetContainer;
        PsiClass[] innerClasses = targetClass.getInnerClasses();
        for (PsiClass innerClass : innerClasses) {
          if (innerClassName.equals(innerClass.getName())) {
            errorString = RefactoringBundle.message("inner.class.exists", innerClassName, targetClass.getName());
            break;
          }
        }
      }
      else {
        LOG.assertTrue(false);
      }
    }

    if (errorString != null) {
      RefactoringMessageUtil.showErrorMessage(
        AnonymousToInnerHandler.REFACTORING_NAME,
        errorString,
        HelpID.ANONYMOUS_TO_INNER,
        myProject);
      myNameField.requestFocusInWindow();
      return;
    }
    super.doOKAction();
    myNameField.requestFocusInWindow();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    JLabel namePrompt = new JLabel(RefactoringBundle.message("anonymousToInner.class.name.label.text"));
    panel.add(namePrompt, gbConstraints);

    myNameField = new EditorTextField("");
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 0;
    panel.add(myNameField, gbConstraints);

    if(!myNeedsThis) {
      myCbMakeStatic = new NonFocusableCheckBox();
      myCbMakeStatic.setText(RefactoringBundle.message("anonymousToInner.make.class.static.checkbox.text"));
      //myCbMakeStatic.setDisplayedMnemonicIndex(11);
      gbConstraints.gridx = 0;
      gbConstraints.gridy++;
      gbConstraints.gridwidth = 2;
      panel.add(myCbMakeStatic, gbConstraints);
      myCbMakeStatic.setSelected(true);
    }
    return panel;
  }

  private JComponent createParametersPanel() {
    JPanel panel = new ParameterTablePanel(myProject, myVariableData) {
      protected void updateSignature() {
      }

      protected void doEnterAction() {
        AnonymousToInnerDialog.this.clickDefaultButton();
      }

      protected void doCancelAction() {
        AnonymousToInnerDialog.this.doCancelAction();
      }
    };
    panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("anonymousToInner.parameters.panel.border.title")));
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createParametersPanel(), BorderLayout.CENTER);
    return panel;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.ANONYMOUS_TO_INNER);
  }
}
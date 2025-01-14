/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 16:54:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.refactoring.ui.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

public class IntroduceParameterDialog extends RefactoringDialog {
  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;

  public static interface Callback {
    void run(IntroduceParameterDialog dialog);
  }

  private Project myProject;
  private List myClassMembersList;
  private int myOccurenceNumber;
  private final boolean myIsInvokedOnDeclaration;
  private boolean myIsLocalVariable;
  private boolean myHasInitializer;

//  private JComponent myParameterNameField = null;
  private NameSuggestionsField myParameterNameField;
  private JCheckBox myCbReplaceAllOccurences = null;
  private JCheckBox myCbDeclareFinal = null;
  private StateRestoringCheckBox myCbDeleteLocalVariable = null;
  private StateRestoringCheckBox myCbUseInitializer = null;

  private JRadioButton myReplaceFieldsWithGettersNoneRadio = null;
  private JRadioButton myReplaceFieldsWithGettersInaccessibleRadio = null;
  private JRadioButton myReplaceFieldsWithGettersAllRadio = null;

  private ButtonGroup myReplaceFieldsWithGettersButtonGroup = new ButtonGroup();

  private Callback myCallback;
  private final NameSuggestionsGenerator myNameSuggestionsGenerator;
  private final TypeSelectorManager myTypeSelectorManager;


  IntroduceParameterDialog(Project project, List localVarsList,
                           List parameterList, List classMembersList, int occurenceNumber,
                           boolean isInvokedOnDeclaration, boolean isLocalVariable,
                           boolean hasInitializer, Callback callback, NameSuggestionsGenerator generator,
                           TypeSelectorManager typeSelectorManager) {
    super(project, true);
    myProject = project;
    myClassMembersList = classMembersList;
    myOccurenceNumber = occurenceNumber;
    myIsInvokedOnDeclaration = isInvokedOnDeclaration;
    myIsLocalVariable = isLocalVariable;
    myHasInitializer = hasInitializer;
    myCallback = callback;
    myNameSuggestionsGenerator = generator;
    myTypeSelectorManager = typeSelectorManager;

    setTitle("Introduce Parameter");
    init();
  }

  public boolean isDeclareFinal() {
    if (myCbDeclareFinal != null) {
      return myCbDeclareFinal.isSelected();
    } else {
      return false;
    }
  }

  public boolean isReplaceAllOccurences() {
    if(myIsInvokedOnDeclaration)
      return true;
    if (myCbReplaceAllOccurences != null) {
      return myCbReplaceAllOccurences.isSelected();
    }
    else
      return false;
  }

  public boolean isDeleteLocalVariable() {
    if(myIsInvokedOnDeclaration)
      return true;
    if(myCbDeleteLocalVariable != null) {
      return myCbDeleteLocalVariable.isSelected();
    }
    else
      return false;
  }

  public boolean isUseInitializer() {
    if(myIsInvokedOnDeclaration)
      return myHasInitializer;
    if(myCbUseInitializer != null) {
      return myCbUseInitializer.isSelected();
    }
    else
      return false;
  }

  public String getParameterName() {
    return  myParameterNameField.getName();
  }

  public int getReplaceFieldsWithGetters() {
    if(myReplaceFieldsWithGettersAllRadio != null && myReplaceFieldsWithGettersAllRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL;
    }
    else if(myReplaceFieldsWithGettersInaccessibleRadio != null
            && myReplaceFieldsWithGettersInaccessibleRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
    }
    else if(myReplaceFieldsWithGettersNoneRadio != null && myReplaceFieldsWithGettersNoneRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE;
    }

    return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
  }

  public JComponent getPreferredFocusedComponent() {
    return myParameterNameField.getComponent();
  }

  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_PARAMETER);
  }

  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.anchor = GridBagConstraints.WEST;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.gridx = 0;

    gbConstraints.insets = new Insets(4, 4, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridy = 0;
    JLabel type = new JLabel("Parameter of type: ");
    panel.add(type, gbConstraints);

    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.gridx++;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);


    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    gbConstraints.fill = GridBagConstraints.NONE;
    final JLabel nameLabel = new JLabel("Name: ");
    panel.add(nameLabel, gbConstraints);

/*
    if (myNameSuggestions.length > 1) {
      myParameterNameField = createComboBoxForName();
    }
    else {
      myParameterNameField = createTextFieldForName();
    }
*/
    myParameterNameField = new NameSuggestionsField(myProject);
    gbConstraints.gridx++;
    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    panel.add(myParameterNameField.getComponent(), gbConstraints);
    myParameterNameField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    });

    myNameSuggestionsManager =
            new NameSuggestionsManager(myTypeSelector, myParameterNameField, myNameSuggestionsGenerator, myProject);
    myNameSuggestionsManager.setMnemonics(type, nameLabel);

    gbConstraints.gridx = 0;
    gbConstraints.insets = new Insets(4, 0, 4, 8);
    gbConstraints.gridwidth = 2;
    if (myOccurenceNumber > 1 && !myIsInvokedOnDeclaration) {
      gbConstraints.gridy++;
      myCbReplaceAllOccurences = new NonFocusableCheckBox("Replace all occurences (" + myOccurenceNumber + " occurences)");
      myCbReplaceAllOccurences.setMnemonic('R');
      panel.add(myCbReplaceAllOccurences, gbConstraints);
      myCbReplaceAllOccurences.setSelected(false);
    }

    RefactoringSettings settings = RefactoringSettings.getInstance();

    gbConstraints.gridy++;
    myCbDeclareFinal = new NonFocusableCheckBox("Declare final");
    myCbDeclareFinal.setMnemonic('n');
    myCbDeclareFinal.setSelected(CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS);
    panel.add(myCbDeclareFinal, gbConstraints);


    if(myIsLocalVariable && !myIsInvokedOnDeclaration) {
      myCbDeleteLocalVariable = new StateRestoringCheckBox("Delete variable definition");
      myCbDeleteLocalVariable.setMnemonic('D');
      if(myCbReplaceAllOccurences != null) {
        gbConstraints.insets = new Insets(0, 16, 4, 8);
      }
      gbConstraints.gridy++;
      panel.add(myCbDeleteLocalVariable, gbConstraints);
      myCbDeleteLocalVariable.setSelected(settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE);

      gbConstraints.insets = new Insets(4, 0, 4, 8);
      if(myHasInitializer) {
        myCbUseInitializer = new StateRestoringCheckBox("Use variable initializer to initialize parameter");
        myCbUseInitializer.setMnemonic('U');
        gbConstraints.gridy++;
        panel.add(myCbUseInitializer, gbConstraints);
      }
    }

    updateControls();
    if (myCbReplaceAllOccurences != null) {
      myCbReplaceAllOccurences.addItemListener(
              new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                  updateControls();
                }
              }
      );
    }
    return panel;
  }

  private void updateControls() {
    if(myCbReplaceAllOccurences != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAllOccurences.isSelected());
      if(myCbReplaceAllOccurences.isSelected()) {
        if (myCbDeleteLocalVariable != null) {
          myCbDeleteLocalVariable.makeSelectable();
        }
      }
      else {
        if (myCbDeleteLocalVariable != null) {
          myCbDeleteLocalVariable.makeUnselectable(false);
        }
      }
    } else {
      myTypeSelectorManager.setAllOccurences(myIsInvokedOnDeclaration);
    }

  }

  private JPanel createReplaceFieldsWithGettersPanel() {
    JPanel radioButtonPanel = new JPanel(new GridBagLayout());

    radioButtonPanel.setBorder(IdeBorderFactory.createBorder());

    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    radioButtonPanel.add(
            new JLabel("Replace fields used in expressions with their getters"), gbConstraints);

    myReplaceFieldsWithGettersNoneRadio =
            new JRadioButton("Do not replace");
    myReplaceFieldsWithGettersNoneRadio.setMnemonic('N');
    myReplaceFieldsWithGettersInaccessibleRadio =
            new JRadioButton("Replace fields inaccessble in usage context");
    myReplaceFieldsWithGettersInaccessibleRadio.setMnemonic('I');
    myReplaceFieldsWithGettersAllRadio =
            new JRadioButton("Replace all fields");
    myReplaceFieldsWithGettersAllRadio.setMnemonic('A');

    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersNoneRadio, gbConstraints);
    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersInaccessibleRadio, gbConstraints);
    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersAllRadio, gbConstraints);

    final int currentSetting = RefactoringSettings.getInstance().INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS;

    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersNoneRadio);
    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersInaccessibleRadio);
    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersAllRadio);

    if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL) {
      myReplaceFieldsWithGettersAllRadio.setSelected(true);
    }
    else if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE) {
      myReplaceFieldsWithGettersInaccessibleRadio.setSelected(true);
    }
    else if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
      myReplaceFieldsWithGettersNoneRadio.setSelected(true);
    }

    return radioButtonPanel;
  }

  protected JComponent createCenterPanel() {
    if(Util.anyFieldsWithGettersPresent(myClassMembersList)) {
      return createReplaceFieldsWithGettersPanel();
    }
    else
      return null;
  }

  protected void doAction() {
    if (!isOKActionEnabled()) return;

    final RefactoringSettings settings = RefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS =
            getReplaceFieldsWithGetters();

    if(myCbDeleteLocalVariable != null) {
      settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE =
              myCbDeleteLocalVariable.isSelectedWhenSelectable();
    }

    myNameSuggestionsManager.nameSelected();
    myCallback.run(this);
    myParameterNameField.requestFocusInWindow();
  }


  protected boolean areButtonsValid () {
    String name = getParameterName();
    if (name == null) {
      return false;
    }
    else {
      return PsiManager.getInstance(myProject).getNameHelper().isIdentifier(name.trim());
    }
  }


}

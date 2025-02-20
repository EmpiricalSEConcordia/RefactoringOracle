
package com.intellij.refactoring.migration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.refactoring.RefactoringBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class EditMigrationEntryDialog extends DialogWrapper{
  private JRadioButton myRbPackage;
  private JRadioButton myRbClass;
  private JTextField myOldNameField;
  private JTextField myNewNameField;
  private Project myProject;

  public EditMigrationEntryDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(RefactoringBundle.message("edit.migration.entry.title"));
    setHorizontalStretch(1.2f);
    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myOldNameField;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    panel.setBorder(IdeBorderFactory.createBorder());
    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.weighty = 1;

    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 0;
    myRbPackage = new JRadioButton(RefactoringBundle.message("migration.entry.package"));
    panel.add(myRbPackage, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 0;
    myRbClass = new JRadioButton(RefactoringBundle.message("migration.entry.class"));
    panel.add(myRbClass, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    panel.add(new JPanel(), gbConstraints);

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbPackage);
    buttonGroup.add(myRbClass);

    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    JLabel oldNamePrompt = new JLabel(RefactoringBundle.message("migration.entry.old.name"));
    panel.add(oldNamePrompt, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    myOldNameField = new JTextField();
    panel.add(myOldNameField, gbConstraints);

    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    JLabel newNamePrompt = new JLabel(RefactoringBundle.message("migration.entry.new.name"));
    panel.add(newNamePrompt, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    myNewNameField = new JTextField();
    panel.setPreferredSize(new Dimension(300, panel.getPreferredSize().height));
    panel.add(myNewNameField, gbConstraints);

    DocumentListener documentListener = new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        validateOKButton();
      }
    };
    myOldNameField.getDocument().addDocumentListener(documentListener);
    myNewNameField.getDocument().addDocumentListener(documentListener);
    return panel;
  }

  private void validateOKButton() {
    boolean isEnabled = true;
    String text = myOldNameField.getText();
    text = text.trim();
    PsiManager manager = PsiManager.getInstance(myProject);
    if (!manager.getNameHelper().isQualifiedName(text)){
      isEnabled = false;
    }
    text = myNewNameField.getText();
    text = text.trim();
    if (!manager.getNameHelper().isQualifiedName(text)){
      isEnabled = false;
    }
    setOKActionEnabled(isEnabled);
  }

  public void setEntry(MigrationMapEntry entry) {
    myOldNameField.setText(entry.getOldName());
    myNewNameField.setText(entry.getNewName());
    myRbPackage.setSelected(entry.getType() == MigrationMapEntry.PACKAGE);
    myRbClass.setSelected(entry.getType() == MigrationMapEntry.CLASS);
    validateOKButton();
  }

  public void updateEntry(MigrationMapEntry entry) {
    entry.setOldName(myOldNameField.getText().trim());
    entry.setNewName(myNewNameField.getText().trim());
    if (myRbPackage.isSelected()){
      entry.setType(MigrationMapEntry.PACKAGE);
      entry.setRecursive(true);
    }
    else{
      entry.setType(MigrationMapEntry.CLASS);
    }
  }
}

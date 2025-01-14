package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.WizardStep;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * author: lesya
 */
public class ImportSettingsStep extends WizardStep {
  private JPanel myPanel;
  private JTextField myModuleName;
  private JTextField myVendor;
  private JTextField myReleaseTag;
  private JTextArea myLogMessage;

  private boolean myIsInitialized = false;
  private File myDirectoryToImport;

  private final SelectImportLocationStep mySelectImportLocationStep;
  private final ImportConfiguration myImportConfiguration;

  private JCheckBox myCheckoutAfterImport;
  private JCheckBox myMakeCheckedOutFilesReadOnly;
  private JLabel myModuleErrorMessage;
  private JLabel myVendorErrorMessage;
  private JLabel myReleaseTagErrorMessage;
  private MyDocumentListener myDocumentListener;
  private JLabel myNameLabel;
  private JLabel myVendorLabel;
  private JLabel myReleaseTagLabel;
  private JLabel myLogMessageLabel;

  public ImportSettingsStep(CvsWizard wizard,
                            SelectImportLocationStep selectImportLocationStep,
                            ImportConfiguration importConfiguration) {
    super("Import Settings", wizard);

    myCheckoutAfterImport.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateCheckoutSettingsVisibility();
      }
    });

    mySelectImportLocationStep = selectImportLocationStep;
    myImportConfiguration = importConfiguration;

    checkFields(null);

    addListenerTo(myModuleName);
    addListenerTo(myVendor);
    addListenerTo(myReleaseTag);

    myLogMessageLabel.setLabelFor(myLogMessage);
    myNameLabel.setLabelFor(myModuleName);
    myReleaseTagLabel.setLabelFor(myReleaseTag);
    myVendorLabel.setLabelFor(myVendor);

    myLogMessage.setWrapStyleWord(true);
    myLogMessage.setLineWrap(true);


    init();
  }

  private void updateCheckoutSettingsVisibility() {
    myMakeCheckedOutFilesReadOnly.setVisible(myCheckoutAfterImport.isSelected());
  }

  protected void dispose() {
  }

  private void addListenerTo(JTextField editor) {
    myDocumentListener = new MyDocumentListener(editor);
    editor.getDocument().addDocumentListener(myDocumentListener);
  }

  public void saveState() {
    super.saveState();
    myImportConfiguration.RELEASE_TAG = getReleaseTag();
    myImportConfiguration.VENDOR = getVendor();
    myImportConfiguration.LOG_MESSAGE = getLogMessage();
    myImportConfiguration.CHECKOUT_AFTER_IMPORT = myCheckoutAfterImport.isSelected();
    myImportConfiguration.MAKE_NEW_FILES_READ_ONLY = myMakeCheckedOutFilesReadOnly.isSelected();
  }

  private boolean checkFields() {
    JTextField[] fields = new JTextField[]{myReleaseTag, myVendor};
    boolean result = CvsFieldValidator.checkField(myModuleName, new JTextField[0], false, myModuleErrorMessage, null);
    result &= CvsFieldValidator.checkField(myVendor, fields, true, myVendorErrorMessage, null);
    result &= CvsFieldValidator.checkField(myReleaseTag, fields, true, myReleaseTagErrorMessage, null);
    return result;
  }

  private void checkFields(JComponent editor) {
    if (!checkFields()) {
      getWizard().disableNextAndFinish();
    }
    else {
      getWizard().enableNextAndFinish();
    }

    if (editor != null) editor.requestFocus();
  }

  public boolean nextIsEnabled() {
    return checkFields();
  }

  public boolean setActive() {
    if (!myIsInitialized) {
      myIsInitialized = true;
      myReleaseTag.setText(myImportConfiguration.RELEASE_TAG);
      myVendor.setText(myImportConfiguration.VENDOR);
      myLogMessage.setText(myImportConfiguration.LOG_MESSAGE);
      myCheckoutAfterImport.setSelected(myImportConfiguration.CHECKOUT_AFTER_IMPORT);
      myMakeCheckedOutFilesReadOnly.setSelected(myImportConfiguration.MAKE_NEW_FILES_READ_ONLY);
      updateCheckoutSettingsVisibility();
      selectAll();
    }
    if (!Comparing.equal(myDirectoryToImport, mySelectImportLocationStep.getSelectedFile())) {
      myDirectoryToImport = mySelectImportLocationStep.getSelectedFile();
      myModuleName.setText(myDirectoryToImport.getName());
      myModuleName.selectAll();
    }
    return true;
  }

  private void selectAll() {
    myLogMessage.selectAll();
    myModuleName.selectAll();
    myReleaseTag.selectAll();
    myVendor.selectAll();
  }

  protected JComponent createComponent() {
    return myPanel;
  }

  public String getVendor() {
    return myVendor.getText().trim();
  }

  public String getReleaseTag() {
    return myReleaseTag.getText().trim();
  }

  public String getLogMessage() {
    return myLogMessage.getText().trim();
  }

  public String getModuleName() {
    return myModuleName.getText().trim();
  }

  private class MyDocumentListener implements DocumentListener {
    private final JComponent myComponent;

    public MyDocumentListener(JComponent component) {
      myComponent = component;
    }

    public void changedUpdate(DocumentEvent e) {
      checkFields(myComponent);
    }

    public void insertUpdate(DocumentEvent e) {
      checkFields(myComponent);
    }

    public void removeUpdate(DocumentEvent e) {
      checkFields(myComponent);
    }
  }
}

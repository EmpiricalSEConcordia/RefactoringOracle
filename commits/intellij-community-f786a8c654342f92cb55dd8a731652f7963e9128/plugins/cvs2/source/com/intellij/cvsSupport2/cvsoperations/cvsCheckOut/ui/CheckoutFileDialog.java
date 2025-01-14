package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.ui;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnVirtualFiles;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui.DateOrRevisionOrTagSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.util.ui.OptionsDialog;

import javax.swing.*;
import java.util.Collection;

/**
 * author: lesya
 */
public class CheckoutFileDialog extends OptionsDialog {

  private final DateOrRevisionOrTagSettings myDateOrRevisionOrTagSettings;

  public CheckoutFileDialog(Project project, Collection<FilePath> files) {
    super(project);
    myDateOrRevisionOrTagSettings = new DateOrRevisionOrTagSettings(
      new TagsProviderOnVirtualFiles(files), project, false);
    myDateOrRevisionOrTagSettings.updateFrom(getCvsConfiguration().CHECKOUT_DATE_OR_REVISION_SETTINGS);
    setTitle(com.intellij.CvsBundle.message("dialog.title.checkout.options"));
    init();
  }


  protected void doOKAction() {
    myDateOrRevisionOrTagSettings.saveTo(getCvsConfiguration().CHECKOUT_DATE_OR_REVISION_SETTINGS);
    super.doOKAction();
  }

  protected JComponent createCenterPanel() {
    return myDateOrRevisionOrTagSettings.getPanel();
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    CvsVcs2.getInstance(myProject).getCheckoutOptions().setValue(value);
  }

  protected boolean isToBeShown() {
    return CvsVcs2.getInstance(myProject).getCheckoutOptions().getValue();
  }

  private CvsConfiguration getCvsConfiguration() {
    return CvsConfiguration.getInstance(myProject);
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }
}

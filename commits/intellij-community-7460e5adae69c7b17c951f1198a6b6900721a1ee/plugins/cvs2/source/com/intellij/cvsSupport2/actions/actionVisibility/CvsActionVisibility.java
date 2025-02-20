package com.intellij.cvsSupport2.actions.actionVisibility;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.actions.cvsContext.CvsLightweightFile;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * author: lesya
 */
public class CvsActionVisibility {

  private boolean myCanBePerformedOnFile = true;
  private boolean myCanBePerformedOnDirectory = true;
  private boolean myCanBePerformedOnSeveralFiles = false;
  private boolean myExpectedCvsAsActiveVcs = true;

  private final List myConditions = new ArrayList();
  private boolean myCanBePerformedOnLocallyDeletedFile = false;
  private boolean myCanBePerformedOnCvsLightweightFile = false;

  public static interface Condition{
    boolean isPerformedOn(CvsContext context);
  }

  public void addCondition(Condition condition){
    myConditions.add(condition);
  }

  public boolean isVisible(CvsContext context){
    if (!myExpectedCvsAsActiveVcs) return true;
    return context.cvsIsActive();
  }

  public boolean isEnabled(CvsContext context){
    boolean result = (context.cvsIsActive() || !myExpectedCvsAsActiveVcs)
        && hasSuitableType(context);

    if (!result) return false;
    for (Iterator each = myConditions.iterator(); each.hasNext();) {
      Condition condition = (Condition) each.next();
      if (!condition.isPerformedOn(context)) return false;
    }
    return true;
  }

  public void shouldNotBePerformedOnFile() {
    myCanBePerformedOnFile = false;
  }

  public void shouldNotBePerformedOnDirectory() {
    myCanBePerformedOnDirectory = false;
  }

  public void canBePerformedOnSeveralFiles() {
    myCanBePerformedOnSeveralFiles = true;
  }

  private boolean hasSuitableType(CvsContext context) {
    File[] selectedIOFiles = context.getSelectedIOFiles();
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    CvsLightweightFile[] lightweightFiles = context.getSelectedLightweightFiles();

    if (selectedFiles == null) selectedFiles = VirtualFile.EMPTY_ARRAY;
    if (selectedIOFiles == null) selectedIOFiles = new File[0];
    if (lightweightFiles == null) lightweightFiles = new CvsLightweightFile[0];

    if ((selectedFiles.length ==0 && lightweightFiles.length == 0) && !myCanBePerformedOnLocallyDeletedFile) return false;
    if ((selectedFiles.length ==0 && selectedIOFiles.length == 0) && !myCanBePerformedOnCvsLightweightFile) return false;

    int selectedFileCount = selectedFiles.length + selectedIOFiles.length + lightweightFiles.length;

    if (selectedFileCount == 0) return false;
    if (selectedFileCount > 1 && !myCanBePerformedOnSeveralFiles) return false;

    if (containsFileFromUnsupportedFileSystem(selectedFiles)) return false;

    if ((containsDirectory(selectedFiles) || containsDirectory(selectedIOFiles)) && !myCanBePerformedOnDirectory) return false;

    if ((containsFile(selectedFiles) || containsFile(selectedIOFiles)) && !myCanBePerformedOnFile) return false;

    return true;
  }

  private boolean containsFileFromUnsupportedFileSystem(VirtualFile[] selectedFiles) {
    for (int i = 0; i < selectedFiles.length; i++) {
      VirtualFile selectedFile = selectedFiles[i];
      if (!(selectedFile.getFileSystem() instanceof LocalFileSystem)) return true;
    }
    return false;
  }

  private boolean containsFile(VirtualFile[] selectedFiles) {
    for (int i = 0; i < selectedFiles.length; i++) {
      VirtualFile selectedFile = selectedFiles[i];
      if (!selectedFile.isDirectory()) return true;
    }
    return false;
  }

  private boolean containsDirectory(VirtualFile[] selectedFiles) {
    for (int i = 0; i < selectedFiles.length; i++) {
      VirtualFile selectedFile = selectedFiles[i];
      if (selectedFile.isDirectory()) return true;
    }
    return false;
  }

  private boolean containsDirectory(File[] selectedFiles) {
    for (int i = 0; i < selectedFiles.length; i++) {
      File selectedFile = selectedFiles[i];
      if (selectedFile.isDirectory()) return true;
    }
    return false;
  }

  private boolean containsFile(File[] selectedFiles) {
    for (int i = 0; i < selectedFiles.length; i++) {
      File selectedFile = selectedFiles[i];
      if (selectedFile.isFile()) return true;
    }
    return false;
  }

  public void canBePerformedOnLocallyDeletedFile() {
    myCanBePerformedOnLocallyDeletedFile = true;
  }

  public void applyToEvent(AnActionEvent e) {
    CvsContext cvsContext = CvsContextWrapper.on(e);
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(isEnabled(cvsContext));
    presentation.setVisible(isVisible(cvsContext));
  }

  public void canBePerformedOnCvsLightweightFile() {
     myCanBePerformedOnCvsLightweightFile = true;
  }


}

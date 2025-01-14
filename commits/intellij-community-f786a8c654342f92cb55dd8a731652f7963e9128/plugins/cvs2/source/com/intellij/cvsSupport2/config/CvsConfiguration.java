package com.intellij.cvsSupport2.config;

import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.util.Options;
import org.netbeans.lib.cvsclient.command.Watch;
import org.jdom.Element;

import java.util.Arrays;
import java.util.List;

/**
 * author: lesya
 */

public class CvsConfiguration implements ProjectComponent, JDOMExternalizable {

  public static final int DO_NOT_MERGE = 0;
  public static final int MERGE_WITH_BRANCH = 1;
  public static final int MERGE_TWO_BRANCHES = 2;


  public boolean PRUNE_EMPTY_DIRECTORIES = true;

  public int MERGING_MODE = DO_NOT_MERGE;
  public String MERGE_WITH_BRANCH1_NAME = CvsUtil.HEAD;
  public String MERGE_WITH_BRANCH2_NAME = CvsUtil.HEAD;
  public boolean RESET_STICKY = false;
  public boolean CREATE_NEW_DIRECTORIES = true;
  public String DEFAULT_TEXT_FILE_SUBSTITUTION = KeywordSubstitutionWrapper.KEYWORD_EXPANSION.getSubstitution().toString();
  
  public boolean PROCESS_UNKNOWN_FILES;
  public boolean PROCESS_DELETED_FILES;
  public boolean PROCESS_IGNORED_FILES;

  public boolean RESERVED_EDIT;
  private final Project myProject;
  public DateOrRevisionSettings CHECKOUT_DATE_OR_REVISION_SETTINGS = new DateOrRevisionSettings();
  public DateOrRevisionSettings UPDATE_DATE_OR_REVISION_SETTINGS = new DateOrRevisionSettings();
  public DateOrRevisionSettings SHOW_CHANGES_REVISION_SETTINGS = new DateOrRevisionSettings();
  public boolean SHOW_OUTPUT = false;
  public int ADD_WATCH_INDEX = 0;
  public List<Watch> WATCHERS = Arrays.asList(new Watch[]{Watch.ALL, Watch.EDIT, Watch.UNEDIT, Watch.COMMIT});
  public int REMOVE_WATCH_INDEX = 0;
  public String UPDATE_KEYWORD_SUBSTITUTION = null;

  public boolean MAKE_NEW_FILES_READONLY = false;
  public int SHOW_CORRUPTED_PROJECT_FILES = Options.SHOW_DIALOG;

  public boolean TAG_AFTER_FILE_COMMIT = false;
  public boolean OVERRIDE_EXISTING_TAG_FOR_FILE = true;
  public String TAG_AFTER_FILE_COMMIT_NAME = "";

  public boolean TAG_AFTER_PROJECT_COMMIT = false;
  public boolean OVERRIDE_EXISTING_TAG_FOR_PROJECT = true;
  public String TAG_AFTER_PROJECT_COMMIT_NAME = "";
  public boolean CLEAN_COPY = false;


  public static CvsConfiguration getInstance(Project project) {
    return project.getComponent(CvsConfiguration.class);
  }

  public CvsConfiguration(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  public String getComponentName() {
    return "Cvs2Configuration";
  }

  public void disposeComponent() {
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  public void initComponent() { }

  public static VcsShowConfirmationOption.Value convertToEnumValue(boolean value, boolean onOk) {
    if (value) {
      return VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    }
    else if (onOk) {
      return VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
    }
    else {
      return VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}

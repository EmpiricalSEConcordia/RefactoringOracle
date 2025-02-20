/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.IdeActions;

import java.util.Collection;
import java.util.Collections;

/**
 * Several {@link DiffContent}s to compare
 */
public abstract class DiffRequest {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.DiffData");
  private String myGroupKey = COMMON_DIFF_GROUP_KEY;
  private final Project myProject;
  private ToolbarAddons myToolbarAddons = ToolbarAddons.NOTHING;
  private static final String COMMON_DIFF_GROUP_KEY = "DiffWindow";

  protected DiffRequest(Project project) {
    myProject = project;
  }

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   */
  public void setToolbarAddons(ToolbarAddons toolbarAddons) {
    LOG.assertTrue(toolbarAddons != null);
    myToolbarAddons = toolbarAddons;
  }

  public String getGroupKey() { return myGroupKey; }
  public void setGroupKey(String groupKey) { myGroupKey = groupKey; }
  public Project getProject() { return myProject; }

  /**
   * @return contents to compare
   */
  public abstract DiffContent[] getContents();

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   */
  public abstract String[] getContentTitles();

  /**
   * Used as window title
   */
  public abstract String getWindowTitle();

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   */
  public void customizeToolbar(DiffToolbar toolbar) {
    myToolbarAddons.customize(toolbar);
  }

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   * @return not null (possibly empty) collection of hints for diff tool.
   */
  public Collection getHints() {
    return Collections.EMPTY_SET;
  }

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   */
  public static interface ToolbarAddons {
    /**
     * Does nothing
     */
    ToolbarAddons NOTHING = new ToolbarAddons() {
      public void customize(DiffToolbar toolbar) {
      }
    };

    /**
     * Removes some of default action to use {@link DiffToolbar} as child of main IDEA frame.
     * Removes actions:<p/>
     * {@link IdeActions#ACTION_COPY}<p/>
     * {@link IdeActions#ACTION_FIND}
     */
    ToolbarAddons IDE_FRAME = new ToolbarAddons() {
      public void customize(DiffToolbar toolbar) {
        toolbar.removeActionById(IdeActions.ACTION_COPY);
        toolbar.removeActionById(IdeActions.ACTION_FIND);
      }
    };
    
    void customize(DiffToolbar toolbar);
  }
}

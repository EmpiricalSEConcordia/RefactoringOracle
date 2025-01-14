/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

public class ComponentWithBrowseButton<Comp extends JComponent> extends JPanel {
  private final Comp myComponent;
  private final MyDoClickAction myDoClickAction;
  private final FixedSizeButton myBrowseButton;
  private boolean myButtonEnabled = true;

  public ComponentWithBrowseButton(Comp component, ActionListener browseActionListener) {
    super(new BorderLayout(2, 0));
    myComponent = component;
    add(myComponent, BorderLayout.CENTER);

    myBrowseButton=new FixedSizeButton(myComponent);
    if (browseActionListener != null)
      myBrowseButton.addActionListener(browseActionListener);
    add(myBrowseButton, BorderLayout.EAST);

    myBrowseButton.setToolTipText("Click or press Alt-Enter");

    // FixedSizeButton isn't focusable but it should be selectable via keyboard.
    myDoClickAction = new MyDoClickAction(myBrowseButton);
    myDoClickAction.registerShortcut(myComponent);
  }

  public final Comp getChildComponent() {
    return myComponent;
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myBrowseButton.setEnabled(enabled && myButtonEnabled);
    myComponent.setEnabled(enabled);
  }

  public void setButtonEnabled(boolean buttonEnabled) {
    myButtonEnabled = buttonEnabled;
    setEnabled(isEnabled());
  }

  public void setButtonIcon(Icon icon) {
    myBrowseButton.setIcon(icon);
  }

  /**
   * Adds specified <code>listener</code> to the browse button.
   */
  public void addActionListener(ActionListener listener){
    myBrowseButton.addActionListener(listener);
  }

  public void addBrowseFolderListener
    (String title, String description, Project project, FileChooserDescriptor fileChooserDescriptor, TextComponentAccessor<Comp> accessor) {
    addActionListener(new BrowseFolderActionListener(title, description, this, project, fileChooserDescriptor, accessor));
  }

  public FixedSizeButton getButton() {
    return myBrowseButton;
  }

  /**
   * Do not use this class directly it is public just to hack other implementation of controls similar to TextFieldWithBrowseButton.
   */
  public static final class MyDoClickAction extends AnAction{
    private final FixedSizeButton myBrowseButton;
    private JComponent myFocusedComponent;
    public MyDoClickAction(FixedSizeButton browseButton) {
      myBrowseButton = browseButton;
    }

    public void actionPerformed(AnActionEvent e){
      myBrowseButton.doClick();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myFocusedComponent != null){
            myFocusedComponent.requestFocus();
          }
        }
      });

    }

    public void registerShortcut(JComponent textField) {
      ShortcutSet shiftEnter = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK));
      registerCustomShortcutSet(shiftEnter, textField);
      myBrowseButton.setToolTipText(KeymapUtil.getShortcutsText(shiftEnter.getShortcuts()));
      myFocusedComponent = textField;
    }

    public static void addTo(FixedSizeButton browseButton, JComponent aComponent) {
      new MyDoClickAction(browseButton).registerShortcut(aComponent);
    }
  }

  public static class BrowseFolderActionListener<T extends JComponent> implements ActionListener {
    private final String myTitle;
    private final String myDescription;
    private final ComponentWithBrowseButton<T> myTextComponent;
    private final TextComponentAccessor<T> myAccessor;
    private final Project myProject;
    private FileChooserDescriptor myFileChooserDescriptor;

    public BrowseFolderActionListener(String title, String description, ComponentWithBrowseButton<T> textField, Project project, FileChooserDescriptor fileChooserDescriptor, TextComponentAccessor<T> accessor) {
      myTitle = title;
      myDescription = description;
      myTextComponent = textField;
      myProject = project;
      myFileChooserDescriptor = fileChooserDescriptor;
      myAccessor = accessor;
    }

    public void actionPerformed(ActionEvent e){
      FileChooserDescriptor fileChooserDescriptor = (FileChooserDescriptor)myFileChooserDescriptor.clone();
      if (myTitle != null) {
        fileChooserDescriptor.setTitle(myTitle);
      }
      if (myDescription != null) {
        fileChooserDescriptor.setDescription(myDescription);
      }
      String directoryName = myAccessor.getText(myTextComponent.getChildComponent()).trim();
      VirtualFile initialFile = LocalFileSystem.getInstance().findFileByPath(directoryName.replace(File.separatorChar, '/'));
      VirtualFile[] files = doChoose(fileChooserDescriptor, initialFile);
      if (files != null && files.length != 0) {
        onFileChoosen(files[0]);
      }
    }

    protected void onFileChoosen(VirtualFile chosenFile) {
      myAccessor.setText(myTextComponent.getChildComponent(), chosenFile.getPresentableUrl());
    }

    private VirtualFile[] doChoose(FileChooserDescriptor fileChooserDescriptor, VirtualFile initialFile) {
      if (myProject == null) {
        return FileChooser.chooseFiles(myTextComponent, fileChooserDescriptor, initialFile);
      }
      else {
        return FileChooser.chooseFiles(myProject, fileChooserDescriptor, initialFile);
      }
    }
  }
}

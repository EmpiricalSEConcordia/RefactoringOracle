/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Sergey.Vasiliev
 */
abstract public class PerspectiveFileEditor extends UserDataHolderBase implements DocumentsEditor, Committable {
  private Wrapper myWrapprer = new Wrapper();
  private boolean myInitialised = false;

  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final Project myProject;
  private final VirtualFile myFile;
  private final UndoHelper myUndoHelper;
  private boolean myInvalidated;

  private static final FileEditorState FILE_EDITOR_STATE = new FileEditorState() {
    public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
      return true;
    }
  };

  protected PerspectiveFileEditor(final Project project, final VirtualFile file) {
    myProject = project;
    myUndoHelper = new UndoHelper(project, this);
    myFile = file;

    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(new FileEditorManagerAdapter() {
      public void selectionChanged(FileEditorManagerEvent event) {
        if (!isValid()) return;

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myUndoHelper.isShowing() && !getComponent().isShowing()) {
              deselectNotify();
            } else if (!myUndoHelper.isShowing() && getComponent().isShowing()) {
              selectNotify();
            }
          }
        });

        final FileEditor oldEditor = event.getOldEditor();
        final FileEditor newEditor = event.getNewEditor();
        if (oldEditor == null || newEditor == null) return;
        if (oldEditor.getComponent().isShowing() && newEditor.getComponent().isShowing()) return;

        if (PerspectiveFileEditor.this.equals(oldEditor)) {
          if (newEditor instanceof TextEditor) {
            ensureInitialized();
            setSelectionInTextEditor((TextEditor)newEditor, getSelectedDomElement());
          }
        }
        else if (PerspectiveFileEditor.this.equals(newEditor)) {
          if (oldEditor instanceof TextEditor) {
            final DomElement element = getSelectedDomElementFromTextEditor((TextEditor)oldEditor);
            if (element != null) {
              ensureInitialized();
              setSelectedDomElement(element);
            }
          }
          else if (oldEditor instanceof PerspectiveFileEditor) {
            ensureInitialized();
            setSelectedDomElement(((PerspectiveFileEditor)oldEditor).getSelectedDomElement());
          }
        }
      }
    }, this);

    myUndoHelper.startListeningDocuments();

    final PsiFile psiFile = getPsiFile();
    if (psiFile != null) {
      final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      if (document != null) {
        addWatchedDocument(document);
      }
    }
  }

  @Nullable
  abstract protected DomElement getSelectedDomElement();

  abstract protected void setSelectedDomElement(DomElement domElement);

  public final void addWatchedElement(@NotNull final DomElement domElement) {
    addWatchedDocument(getDocumentManager().getDocument(domElement.getRoot().getFile()));
  }

  public final void removeWatchedElement(@NotNull final DomElement domElement) {
    removeWatchedDocument(getDocumentManager().getDocument(domElement.getRoot().getFile()));
  }

  public final void addWatchedDocument(final Document document) {
    myUndoHelper.addWatchedDocument(document);
  }

  public final void removeWatchedDocument(final Document document) {
    myUndoHelper.removeWatchedDocument(document);
  }

  @Nullable
  protected DomElement getSelectedDomElementFromTextEditor(final TextEditor textEditor) {
    final PsiFile psiFile = getPsiFile();
    if (psiFile == null) return null;
    final PsiElement psiElement = psiFile.findElementAt(textEditor.getEditor().getCaretModel().getOffset());

    if (psiElement == null) return null;

    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);

    return DomManager.getDomManager(myProject).getDomElement(xmlTag);
  }

  public void setSelectionInTextEditor(final TextEditor textEditor, final DomElement element) {
    if (element != null && element.isValid()) {
      final XmlTag tag = element.getXmlTag();
      if (tag == null) return;

      final PsiFile file = tag.getContainingFile();
      if (file == null) return;

      final Document document = getDocumentManager().getDocument(file);
      if (document == null || !document.equals(textEditor.getEditor().getDocument())) return;

      textEditor.getEditor().getCaretModel().moveToOffset(tag.getTextOffset());
      textEditor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  protected final PsiDocumentManager getDocumentManager() {
    return PsiDocumentManager.getInstance(myProject);
  }

  @Nullable
  public final PsiFile getPsiFile() {
    return PsiManager.getInstance(myProject).findFile(myFile);
  }

  public final Document[] getDocuments() {
    return myUndoHelper.getDocuments();
  }

  public final Project getProject() {
    return myProject;
  }

  public final VirtualFile getVirtualFile() {
    return myFile;
  }

  public void dispose() {
    if (myInvalidated) return;
    myInvalidated = true;
    myUndoHelper.stopListeningDocuments();
  }

  public final boolean isModified() {
    return FileDocumentManager.getInstance().isFileModified(getVirtualFile());
  }

  public boolean isValid() {
    return getVirtualFile().isValid();
  }

  public void selectNotify() {
    if (!checkIsValid() || myInvalidated) return;
    ensureInitialized();
    setShowing(true);
    reset();
  }

  protected final void setShowing(final boolean b) {
    myUndoHelper.setShowing(b);
  }

  protected final synchronized void ensureInitialized() {
    if (!isInitialised()) {
      myWrapprer.setContent(createCustomComponent());
      myInitialised = true;
    }
  }

  public void deselectNotify() {
    if (!checkIsValid() || myInvalidated) return;
    setShowing(false);
    commit();
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return new FileEditorLocation() {
      public FileEditor getEditor() {
        return PerspectiveFileEditor.this;
      }

      public int compareTo(final FileEditorLocation fileEditorLocation) {
        return 0;
      }
    };
  }

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FILE_EDITOR_STATE;
  }

  public void setState(@NotNull FileEditorState state) {
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  protected boolean checkIsValid() {
    if (!myInvalidated && !isValid()) {
      myInvalidated = true;
      myPropertyChangeSupport.firePropertyChange(FileEditor.PROP_VALID, Boolean.TRUE, Boolean.FALSE);
    }
    return !myInvalidated;
  }

  @NotNull
  public JComponent getComponent() {
    return getWrapper();
  }

  @NotNull
  protected abstract JComponent createCustomComponent();

  public Wrapper getWrapper() {
    return myWrapprer;
  }

  protected final synchronized boolean isInitialised() {
    return myInitialised;
  }
}

package com.intellij.html.preview;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * @author spleaner
 */
public class ImageOrColorPreviewManager implements Disposable, EditorMouseMotionListener, KeyListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.html.preview.ImageOrColorPreviewManager");
  private MergingUpdateQueue myQueue;
  private Editor myEditor;
  private PsiFile myFile;
  private LightweightHint myHint;
  private PsiElement myElement;

  public ImageOrColorPreviewManager(@NotNull final TextEditor editor) {
    myEditor = editor.getEditor();

    myEditor.addEditorMouseMotionListener(this);
    myEditor.getContentComponent().addKeyListener(this);

    Document document = myEditor.getDocument();
    myFile = PsiDocumentManager.getInstance(myEditor.getProject()).getPsiFile(document);


    final JComponent component = editor.getEditor().getComponent();
    myQueue = new MergingUpdateQueue("ImageOrColorPreview", 100, component.isShowing(), component);
    Disposer.register(this, new UiNotifyConnector(editor.getComponent(), myQueue));
  }

  public void keyTyped(final KeyEvent e) {
  }

  public void keyPressed(final KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
      if (myEditor != null) {
        final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo != null) {
          final Point location = pointerInfo.getLocation();
          SwingUtilities.convertPointFromScreen(location, myEditor.getContentComponent());

          myQueue.cancelAllUpdates();
          myQueue.queue(new PreviewUpdate(this, location));
        }
      }
    }
  }

  public void keyReleased(final KeyEvent e) {
  }

  public Editor getEditor() {
    return myEditor;
  }

  @Nullable
  private PsiElement getPsiElementAt(@NotNull final Point point) {
    final LogicalPosition position = getLogicalPosition_(point);
    if (myFile != null && !(myFile instanceof PsiCompiledElement)) {
      return myFile.getViewProvider().findElementAt(myEditor.logicalPositionToOffset(position));
    }

    return null;
  }

  private LogicalPosition getLogicalPosition(final PsiElement element) {
    return myEditor.offsetToLogicalPosition(element.getTextRange().getEndOffset());
  }

  private LogicalPosition getLogicalPosition_(final Point point) {
    return myEditor.xyToLogicalPosition(point);
  }

  private void setCurrentHint(final LightweightHint hint, final PsiElement element) {
    if (hint != null) {
      myHint = hint;
      myElement = element;
    }
  }

  private void showHint(final LightweightHint hint, final PsiElement element, Editor editor) {
    if (element != myElement && element.isValid()) {
      hideCurrentHintIfAny();
      setCurrentHint(hint, element);

      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManagerImpl.getHintPosition(hint, editor, getLogicalPosition(element),
                                                                                         HintManagerImpl.RIGHT_UNDER), HintManagerImpl
        .HIDE_BY_ANY_KEY | HintManagerImpl.HIDE_BY_OTHER_HINT | HintManagerImpl.HIDE_BY_SCROLLING | HintManagerImpl.HIDE_BY_TEXT_CHANGE | HintManagerImpl
        .HIDE_IF_OUT_OF_EDITOR, 0, false);
    }
  }

  private void hideCurrentHindIfOutOfElement(final PsiElement e) {
    if (myHint != null && e != myElement) {
      hideCurrentHintIfAny();
    }
  }

  private void hideCurrentHintIfAny() {
    if (myHint != null) {
      myHint.hide();
      myHint = null;
      myElement = null;
    }
  }

  public void dispose() {
    if (myEditor != null) {
      myEditor.removeEditorMouseMotionListener(this);
      myEditor.getContentComponent().removeKeyListener(this);
    }

    if (myQueue != null) {
      myQueue.cancelAllUpdates();
      myQueue.hideNotify();
    }

    myQueue = null;
    myEditor = null;
    myFile = null;
    myHint = null;
  }

  public void mouseMoved(EditorMouseEvent e) {
    myQueue.cancelAllUpdates();
    if (myHint == null && e.getMouseEvent().getModifiers() == KeyEvent.SHIFT_MASK) {
      myQueue.queue(new PreviewUpdate(this, e.getMouseEvent().getPoint()));
    }
    else {
      hideCurrentHindIfOutOfElement(getPsiElementAt(e.getMouseEvent().getPoint()));
    }
  }

  public void mouseDragged(EditorMouseEvent e) {
    // nothing
  }

  @Nullable
  private static LightweightHint getHint(@NotNull PsiElement element) {
    try {
      JComponent preview = ColorPreviewComponent.getPreviewComponent(element);
      if (preview == null) {
        preview = ImagePreviewComponent.getPreviewComponent(element);
      }

      return preview == null ? null : new LightweightHint(preview);
    }
    catch (Exception e) {
      LOG.error(e);
    }

    return null;
  }

  private static final class PreviewUpdate extends Update {
    private final ImageOrColorPreviewManager myManager;
    private final Point myPoint;

    public PreviewUpdate(@NonNls final ImageOrColorPreviewManager manager, @NotNull final Point point) {
      super(manager);

      myManager = manager;
      myPoint = point;
    }

    public void run() {
      final PsiElement element = myManager.getPsiElementAt(myPoint);
      if (element != null && element.isValid()) {
        if (PsiDocumentManager.getInstance(element.getProject()).isUncommited(myManager.getEditor().getDocument())) {
          return;
        }

        final LightweightHint hint = ImageOrColorPreviewManager.getHint(element);
        if (hint != null) {
          final Editor editor = myManager.getEditor();
          if (editor != null) {
            myManager.showHint(hint, element, editor);
          }
        }
        else {
          myManager.hideCurrentHintIfAny();
        }
      }
    }

    public boolean canEat(final Update update) {
      return true;
    }
  }
}

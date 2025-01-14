/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.Patches;
import com.intellij.codeInsight.hint.DocumentFragmentTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.*;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.impl.event.MarkupModelEvent;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TestableUi;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.JScrollPane2;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.EmptyClipboardOwner;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import gnu.trove.TIntArrayList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class EditorImpl extends UserDataHolderBase implements EditorEx, HighlighterClient, TestableUi {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorImpl");
  private static final Key DND_COMMAND_KEY = Key.create("DndCommand");
  public static final Key<Boolean> DO_DOCUMENT_UPDATE_TEST = Key.create("DoDocumentUpdateTest");
  private final DocumentImpl myDocument;

  private final JPanel myPanel;
  private final JScrollPane myScrollPane;
  private final EditorComponentImpl myEditorComponent;
  private final EditorGutterComponentImpl myGutterComponent;

  static {
    @SuppressWarnings({"UnusedDeclaration"})
    ComplementaryFontsRegistry registry; // load costly font info
  }

  private final CommandProcessor myCommandProcessor;
  private final MyScrollBar myVerticalScrollBar;

  private final CopyOnWriteArrayList<EditorMouseListener> myMouseListeners = ContainerUtil.createEmptyCOWList();
  private final CopyOnWriteArrayList<EditorMouseMotionListener> myMouseMotionListeners;

  private int myCharHeight = -1;
  private int myLineHeight = -1;
  private int myDescent = -1;

  private boolean myIsInsertMode = true;

  private final CaretCursor myCaretCursor;
  private final ScrollingTimer myScrollingTimer = new ScrollingTimer();

  private final Key<Object> MOUSE_DRAGGED_GROUP = Key.create("MouseDraggedGroup");

  private final DocumentListener myEditorDocumentAdapter;

  private final SettingsImpl mySettings;

  private boolean isReleased = false;

  private MouseEvent myMousePressedEvent = null;

  private int mySavedSelectionStart = -1;
  private int mySavedSelectionEnd = -1;
  private int myLastColumnNumber = 0;

  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private MyEditable myEditable;

  private EditorColorsScheme myScheme;
  private final boolean myIsViewer;
  private final SelectionModelImpl mySelectionModel;
  private final EditorMarkupModelImpl myMarkupModel;
  private final FoldingModelImpl myFoldingModel;
  private final ScrollingModelImpl myScrollingModel;
  private final CaretModelImpl myCaretModel;

  private static final RepaintCursorCommand ourCaretBlinkingCommand;

//  private final BorderEffect myBorderEffect = new BorderEffect();

  private int myMouseSelectionState = MOUSE_SELECTION_STATE_NONE;
  private FoldRegion myMouseSelectedRegion = null;

  private static final int MOUSE_SELECTION_STATE_NONE = 0;
  private static final int MOUSE_SELECTION_STATE_WORD_SELECTED = 1;
  private static final int MOUSE_SELECTION_STATE_LINE_SELECTED = 2;

  private final MarkupModelListener myMarkupModelListener;

  private EditorHighlighter myHighlighter;

  private int myScrollbarOrientation;
  private boolean myMousePressedInsideSelection;
  private FontMetrics myPlainFontMetrics;
  private FontMetrics myBoldFontMetrics;
  private FontMetrics myItalicFontMetrics;
  private FontMetrics myBoldItalicFontMetrics;

  private static final int CACHED_CHARS_BUFFER_SIZE = 300;

  private final ArrayList<CachedFontContent> myFontCache = new ArrayList<CachedFontContent>();
  private FontInfo myCurrentFontType = null;

  private final EditorSizeContainer mySizeContainer = new EditorSizeContainer();

  private Runnable myCursorUpdater;
  private int myCaretUpdateVShift;
  private final Project myProject;
  private long myMouseSelectionChangeTimestamp;
  private int mySavedCaretOffsetForDNDUndoHack;
  private final ArrayList<FocusChangeListener> myFocusListeners = new ArrayList<FocusChangeListener>();

  private MyInputMethodHandler myInputMethodRequestsHandler;
  private InputMethodRequests myInputMethodRequestsSwingWrapper;
  private boolean myIsOneLineMode;
  private boolean myIsRendererMode;
  private VirtualFile myVirtualFile;
  private boolean myIsColumnMode = false;
  private Color myForcedBackground = null;
  private Dimension myPreferredSize;
  private Runnable myGutterSizeUpdater = null;
  private boolean myGutterNeedsUpdate = false;
  private Alarm myAppleRepaintAlarm;
  private boolean myEmbeddedIntoDialogWrapper;
  private CachedFontContent myLastCache;
  private boolean mySpacesHaveSameWidth;

  private Point myLastBackgroundPosition = null;
  private Color myLastBackgroundColor = null;
  private int myLastBackgroundWidth;
  private static final boolean ourIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private final JPanel myHeaderPanel;

  private MouseEvent myInitialMouseEvent;
  private boolean myIgnoreMouseEventsConsecutiveToInitial;

  private String myReleasedAt = null;

  private EditorDropHandler myDropHandler;

  static {
    ourCaretBlinkingCommand = new RepaintCursorCommand();
    ourCaretBlinkingCommand.start();
  }


  public EditorImpl(Document document, boolean viewer, Project project) {
    myProject = project;
    myDocument = (DocumentImpl)document;
    myScheme = new MyColorSchemeDelegate();
    myIsViewer = viewer;
    mySettings = new SettingsImpl(this);

    mySelectionModel = new SelectionModelImpl(this);
    myMarkupModel = new EditorMarkupModelImpl(this);
    myFoldingModel = new FoldingModelImpl(this);
    myCaretModel = new CaretModelImpl(this);
    mySizeContainer.reset();

    myCommandProcessor = CommandProcessor.getInstance();

    myEditorDocumentAdapter = new EditorDocumentAdapter();
    myMouseMotionListeners = ContainerUtil.createEmptyCOWList();

    myMarkupModelListener = new MarkupModelListener() {
      public void rangeHighlighterChanged(MarkupModelEvent event) {
        assertIsDispatchThread();

        RangeHighlighterImpl rangeHighlighter = (RangeHighlighterImpl)event.getHighlighter();
        if (rangeHighlighter.isValid()) {
          int start = rangeHighlighter.getAffectedAreaStartOffset();
          int end = rangeHighlighter.getAffectedAreaEndOffset();
          int startLine = myDocument.getLineNumber(start);
          int endLine = myDocument.getLineNumber(end);
          repaintLines(Math.max(0, startLine - 1), Math.min(endLine + 1, getDocument().getLineCount()));
        }
        else {
          repaint(0, getDocument().getTextLength());
        }
        ((EditorMarkupModelImpl)getMarkupModel()).repaint();
        ((EditorMarkupModelImpl)getMarkupModel()).markDirtied();
        GutterIconRenderer renderer = rangeHighlighter.getGutterIconRenderer();
        if (renderer != null) {
          updateGutterSize();
        }
        updateCaretCursor();
      }
    };

    ((MarkupModelEx)myDocument.getMarkupModel(myProject)).addMarkupModelListener(myMarkupModelListener);
    ((MarkupModelEx)getMarkupModel()).addMarkupModelListener(myMarkupModelListener);

    myDocument.addDocumentListener(myFoldingModel);
    myDocument.addDocumentListener(myCaretModel);
    myDocument.addDocumentListener(mySelectionModel);
    myDocument.addDocumentListener(myEditorDocumentAdapter);

    myCaretCursor = new CaretCursor();

    myFoldingModel.flushCaretShift();
    myScrollbarOrientation = VERTICAL_SCROLLBAR_RIGHT;

    EditorHighlighter highlighter = new EmptyEditorHighlighter(myScheme.getAttributes(HighlighterColors.TEXT));
    setHighlighter(highlighter);

    myEditorComponent = new EditorComponentImpl(this);
    myScrollPane = new MyScrollPane();
    myPanel = new JPanel() {
      public void addNotify() {
        super.addNotify();
        if (((JComponent)getParent()).getBorder() != null) myScrollPane.setBorder(null);
      }
    };

    myHeaderPanel = new MyHeaderPanel();
    myVerticalScrollBar = new MyScrollBar(Adjustable.VERTICAL);
    myGutterComponent = new EditorGutterComponentImpl(this);
    initComponent();

    myScrollingModel = new ScrollingModelImpl(this);

    myGutterComponent.updateSize();
    Dimension preferredSize = getPreferredSize();
    myEditorComponent.setSize(preferredSize);

    if (Patches.APPLE_BUG_ID_3716835) {
      myScrollingModel.addVisibleAreaListener(new VisibleAreaListener() {
        public void visibleAreaChanged(VisibleAreaEvent e) {
          if (myAppleRepaintAlarm == null) {
            myAppleRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
          }
          myAppleRepaintAlarm.cancelAllRequests();
          myAppleRepaintAlarm.addRequest(new Runnable() {
            public void run() {
              repaint(0, myDocument.getTextLength());
            }
          }, 50, ModalityState.stateForComponent(myEditorComponent));
        }
      });
    }

    updateCaretCursor();

    // This hacks context layout problem where editor appears scrolled to the right just after it is created.
    if (!ourIsUnitTestMode) {
      UiNotifyConnector.doWhenFirstShown(myEditorComponent, new Runnable() {
        public void run() {
          if (!isDisposed() && !myScrollingModel.isScrollingNow()) {
            myScrollingModel.disableAnimation();
            myScrollingModel.scrollHorizontally(0);
            myScrollingModel.enableAnimation();
          }
        }
      });
    }
  }

  public boolean isViewer() {
    return myIsViewer || myIsRendererMode;
  }

  public boolean isRendererMode() {
    return myIsRendererMode;
  }

  public void setRendererMode(boolean isRendererMode) {
    myIsRendererMode = isRendererMode;
  }

  public void setFile(VirtualFile vFile) {
    myVirtualFile = vFile;
    reinitSettings();
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    return myMarkupModel;
  }

  @NotNull
  public FoldingModel getFoldingModel() {
    return myFoldingModel;
  }

  @NotNull
  public CaretModel getCaretModel() {
    return myCaretModel;
  }

  @NotNull
  public ScrollingModel getScrollingModel() {
    return myScrollingModel;
  }

  @NotNull
  public EditorSettings getSettings() {
    assertReadAccess();
    return mySettings;
  }

  public void reinitSettings() {
    assertIsDispatchThread();
    myCharHeight = -1;
    myLineHeight = -1;
    myDescent = -1;
    myPlainFontMetrics = null;

    myCaretModel.reinitSettings();
    mySelectionModel.reinitSettings();
    mySettings.reinitSettings();
    ourCaretBlinkingCommand.setBlinkCaret(mySettings.isBlinkCaret());
    ourCaretBlinkingCommand.setBlinkPeriod(mySettings.getCaretBlinkPeriod());
    mySizeContainer.reset();
    myFoldingModel.refreshSettings();
    myFoldingModel.rebuild();

    if (myScheme instanceof MyColorSchemeDelegate) {
      ((MyColorSchemeDelegate)myScheme).updateGlobalScheme();
    }
    myHighlighter.setColorScheme(myScheme);

    myGutterComponent.reinitSettings();
    myGutterComponent.revalidate();

    myEditorComponent.repaint();

    updateCaretCursor();

    if (myInitialMouseEvent != null) {
      myIgnoreMouseEventsConsecutiveToInitial = true;
    }
  }

  public void release() {
    if (isReleased) {
      LOG.error("Double release. First released at:  =====\n" + myReleasedAt+"\n======");
    }

    myReleasedAt = StringUtil.getThrowableText(new Throwable());

    isReleased = true;
    myDocument.removeDocumentListener(myHighlighter);
    myDocument.removeDocumentListener(myEditorDocumentAdapter);
    myDocument.removeDocumentListener(myFoldingModel);
    myDocument.removeDocumentListener(myCaretModel);
    myDocument.removeDocumentListener(mySelectionModel);

    MarkupModelEx markupModel = (MarkupModelEx)myDocument.getMarkupModel(myProject, false);
    if (markupModel instanceof MarkupModelImpl) {
      markupModel.removeMarkupModelListener(myMarkupModelListener);
    }

    myMarkupModel.dispose();

    myLineHeight = -1;
    myCharHeight = -1;
    myDescent = -1;
    myPlainFontMetrics = null;
    myScrollingModel.dispose();
    myGutterComponent.dispose();
    clearCaretThread();
    //myFoldingModel.dispose(); TODO rangemarker tree
  }

  private void clearCaretThread() {
    synchronized (ourCaretBlinkingCommand) {
      if (ourCaretBlinkingCommand.myEditor == this) {
        ourCaretBlinkingCommand.myEditor = null;
      }
    }
  }

  private void initComponent() {
//    myStatusBar = new EditorStatusBarImpl();

    //myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));
    myPanel.setLayout(new BorderLayout());

    myPanel.add(myHeaderPanel, BorderLayout.NORTH);

    myGutterComponent.setOpaque(true);

    myScrollPane.setVerticalScrollBar(myVerticalScrollBar);
    final MyScrollBar horizontalScrollBar = new MyScrollBar(Adjustable.HORIZONTAL);
    myScrollPane.setHorizontalScrollBar(horizontalScrollBar);
    myScrollPane.setViewportView(myEditorComponent);
    //myScrollPane.setBorder(null);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);


    myScrollPane.setRowHeaderView(myGutterComponent);
    stopOptimizedScrolling();

    myEditorComponent.setTransferHandler(new MyTransferHandler());
    myEditorComponent.setAutoscrolls(true);

/*  Default mode till 1.4.0
 *   myScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
 */

    if (mayShowToolbar()) {
      JLayeredPane layeredPane = new JLayeredPane() {
        @Override
        public void doLayout() {
          final Component[] components = getComponents();
          final Rectangle r = getBounds();
          for (Component c : components) {
            if (c instanceof JScrollPane) {
              c.setBounds(0, 0, r.width, r.height);
            }
            else {
              final Dimension d = c.getPreferredSize();
              final MyScrollBar scrollBar = getVerticalScrollBar();
              c.setBounds(r.width - d.width - scrollBar.getWidth() - 30, 20, d.width, d.height);
            }
          }
        }
      };

      layeredPane.add(myScrollPane, JLayeredPane.DEFAULT_LAYER);
      myPanel.add(layeredPane);

      new ContextMenuImpl(layeredPane, myScrollPane, this);
    }
    else {
      myPanel.add(myScrollPane);
    }

    //myPanel.add(myScrollPane);

    myEditorComponent.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent event) {
        if (Patches.APPLE_BUG_ID_3337563) return; // Everything is going through InputMethods under MacOS X in JDK releases earlier than 1.4.2_03-117.1
        if (event.isConsumed()) {
          return;
        }
        if (processKeyTyped(event)) {
          event.consume();
        }
      }
    });

    MyMouseAdapter mouseAdapter = new MyMouseAdapter();
    myEditorComponent.addMouseListener(mouseAdapter);
    myGutterComponent.addMouseListener(mouseAdapter);

    MyMouseMotionListener mouseMotionListener = new MyMouseMotionListener();
    myEditorComponent.addMouseMotionListener(mouseMotionListener);
    myGutterComponent.addMouseMotionListener(mouseMotionListener);

    myEditorComponent.addFocusListener(new FocusAdapter() {
      public void focusGained(FocusEvent e) {
        myCaretCursor.activate();
        int caretLine = getCaretModel().getLogicalPosition().line;
        repaintLines(caretLine, caretLine);
        fireFocusGained();
        if (myGutterNeedsUpdate) {
          updateGutterSize();
        }
      }

      public void focusLost(FocusEvent e) {
        clearCaretThread();
        int caretLine = getCaretModel().getLogicalPosition().line;
        repaintLines(caretLine, caretLine);
        fireFocusLost();
      }
    });

//    myBorderEffect.reset();
    try {
      final DropTarget dropTarget = myEditorComponent.getDropTarget();
      if (dropTarget != null) { // might be null in headless environment
        dropTarget.addDropTargetListener(new DropTargetAdapter() {
          public void drop(DropTargetDropEvent dtde) {
          }

          public void dragOver(DropTargetDragEvent dtde) {
            Point location = dtde.getLocation();

            moveCaretToScreenPos(location.x, location.y);
            getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
        });
      }
    }
    catch (TooManyListenersException e) {
      LOG.error(e);
    }

    myPanel.addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        myMarkupModel.repaint();
      }
    });
  }

  private boolean mayShowToolbar() {
    return !isEmbeddedIntoDialogWrapper() && !isOneLineMode() && ContextMenuImpl.mayShowToolbar(myDocument);
  }

  public void setFontSize(final int fontSize) {
    int oldFontSize = myScheme.getEditorFontSize();
    myScheme.setEditorFontSize(fontSize);
    myPropertyChangeSupport.firePropertyChange(PROP_FONT_SIZE, oldFontSize, fontSize);
  }

  public ActionCallback type(final String text) {
    final ActionCallback result = new ActionCallback();

    Application app = ApplicationManager.getApplication();
    if (!app.isWriteAccessAllowed()) {
      result.setRejected();
    } else {
      app.runWriteAction(new Runnable() {
        public void run() {
          for (int i = 0; i < text.length(); i++) {
            if (!processKeyTyped(text.charAt(i))) {
              result.setRejected();
              return;
            }
          }

          result.setDone();
        }
      });
    }

    return result;
  }

  private boolean processKeyTyped(char c) {
    // [vova] This is patch for Mac OS X. Under Mac "input methods"
    // is handled before our EventQueue consume upcoming KeyEvents.
    IdeEventQueue queue = IdeEventQueue.getInstance();
    if (queue.shouldNotTypeInEditor() || ProgressManager.getInstance().hasModalProgressIndicator()) {
      return false;
    }
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    DataContext dataContext = getDataContext();
    actionManager.fireBeforeEditorTyping(c, dataContext);
    EditorActionManager.getInstance().getTypedAction().actionPerformed(this, c, dataContext);

    return true;
  }

  private void fireFocusLost() {
    FocusChangeListener[] listeners = getFocusListeners();
    for (FocusChangeListener listener : listeners) {
      listener.focusLost(this);
    }
  }

  private FocusChangeListener[] getFocusListeners() {
    return myFocusListeners.toArray(new FocusChangeListener[myFocusListeners.size()]);
  }

  private void fireFocusGained() {
    FocusChangeListener[] listeners = getFocusListeners();
    for (FocusChangeListener listener : listeners) {
      listener.focusGained(this);
    }
  }

  public void setHighlighter(EditorHighlighter highlighter) {
    assertIsDispatchThread();
    final Document document = getDocument();
    if (myHighlighter != null) {
      document.removeDocumentListener(myHighlighter);
    }

    document.addDocumentListener(highlighter);
    highlighter.setEditor(this);
    highlighter.setText(document.getCharsSequence());
    myHighlighter = highlighter;
    if (document instanceof DocumentImpl) {
      ((DocumentImpl)document).rememberEditorHighlighterForCachesOptimization(highlighter);
    }

    if (myPanel != null) {
      reinitSettings();
    }
  }

  public EditorHighlighter getHighlighter() {
    assertIsDispatchThread();
    return myHighlighter;
  }

  @NotNull
  public JComponent getContentComponent() {
    return myEditorComponent;
  }

  public EditorGutterComponentEx getGutterComponentEx() {
    return myGutterComponent;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public void setInsertMode(boolean mode) {
    assertIsDispatchThread();
    boolean oldValue = myIsInsertMode;
    myIsInsertMode = mode;
    myPropertyChangeSupport.firePropertyChange(PROP_INSERT_MODE, oldValue, mode);
    //Repaint the caret line by moving caret to the same place
    LogicalPosition caretPosition = getCaretModel().getLogicalPosition();
    getCaretModel().moveToLogicalPosition(caretPosition);
  }

  public boolean isInsertMode() {
    return myIsInsertMode;
  }

  public void setColumnMode(boolean mode) {
    assertIsDispatchThread();
    boolean oldValue = myIsColumnMode;
    myIsColumnMode = mode;
    myPropertyChangeSupport.firePropertyChange(PROP_COLUMN_MODE, oldValue, mode);
  }

  public boolean isColumnMode() {
    return myIsColumnMode;
  }

  private int yPositionToVisibleLineNumber(int y) {
    return y / getLineHeight();
  }

  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    int line = yPositionToVisibleLineNumber(p.y);

    int offset = logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(line, 0)));
    int textLength = myDocument.getTextLength();

    if (offset >= textLength) return new VisualPosition(line, 0);

    int column = 0;
    int prevX = 0;
    CharSequence text = myDocument.getCharsNoThreadCheck();
    char c = ' ';
    IterationState state = new IterationState(this, offset, false);

    int fontType = state.getMergedAttributes().getFontType();
    int spaceSize = EditorUtil.getSpaceWidth(fontType, this);

    int x = 0;
    outer:
    while (true) {
      if (offset >= textLength) break;

      if (offset >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }

      FoldRegion region = state.getCurrentFold();
      if (region != null) {
        char[] placeholder = region.getPlaceholderText().toCharArray();
        for (char aPlaceholder : placeholder) {
          c = aPlaceholder;
          x += EditorUtil.charWidth(c, fontType, this);
          if (x >= p.x) break outer;
          column++;
        }
        offset = region.getEndOffset();
      }
      else {
        prevX = x;
        c = text.charAt(offset);
        if (c == '\n') {
          break;
        }
        if (c == '\t') {
          x = EditorUtil.nextTabStop(x, this);
        }
        else {
          x += EditorUtil.charWidth(c, fontType, this);
        }

        if (x >= p.x) break;

        if (c == '\t') {
          column += (x - prevX) / spaceSize;
        }
        else {
          column++;
        }

        offset++;
      }
    }

    int charWidth = EditorUtil.charWidth(c, fontType, this);

    if (x >= p.x && c == '\t') {
      if (mySettings.isCaretInsideTabs()) {
        column += (p.x - prevX) / spaceSize;
        if ((p.x - prevX) % spaceSize > spaceSize / 2) column++;
      }
      else {
        if ((x - p.x) * 2 < x - prevX) {
          column += (x - prevX) / spaceSize;
        }
      }
    }
    else {
      if (x >= p.x) {
        if ((x - p.x) * 2 < charWidth) column++;
      }
      else {
        column += (p.x - x) / EditorUtil.getSpaceWidth(fontType, this);
      }
    }

    return new VisualPosition(line, column);
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    int line = calcLogicalLineNumber(offset);
    int column = calcColumnNumber(offset, line);
    return new LogicalPosition(line, column);
  }

  @NotNull
  public LogicalPosition xyToLogicalPosition(@NotNull Point p) {
    final Point pp;
    if (p.x >= 0 && p.y >= 0) {
      pp = p;
    }
    else {
      pp = new Point(Math.max(p.x, 0), Math.max(p.y, 0));
    }

    return visualToLogicalPosition(xyToVisualPosition(pp));
  }

  private int logicalLineToY(int line) {
    VisualPosition visible = logicalToVisualPosition(new LogicalPosition(line, 0));
    return visibleLineNumberToYPosition(visible.line);
  }

  @NotNull
  public Point logicalPositionToXY(@NotNull LogicalPosition pos) {
    VisualPosition visible = logicalToVisualPosition(pos);
    int y = visibleLineNumberToYPosition(visible.line);

    int lineStartOffset;

    if (pos.line == 0) {
      lineStartOffset = 0;
    }
    else {
      if (pos.line >= myDocument.getLineCount()) {
        lineStartOffset = myDocument.getTextLength();
      }
      else {
        lineStartOffset = myDocument.getLineStartOffset(pos.line);
      }
    }

    int x = getTabbedTextWidth(lineStartOffset, visible);
    return new Point(x, y);
  }

  @NotNull
  public Point visualPositionToXY(@NotNull VisualPosition visible) {
    int y = visibleLineNumberToYPosition(visible.line);
    int logLine = visualToLogicalPosition(new VisualPosition(visible.line, 0)).line;

    int lineStartOffset;

    if (logLine == 0) {
      lineStartOffset = 0;
    }
    else {
      if (logLine >= myDocument.getLineCount()) {
        lineStartOffset = myDocument.getTextLength();
      }
      else {
        lineStartOffset = myDocument.getLineStartOffset(logLine);
      }
    }

    int x = getTabbedTextWidth(lineStartOffset, visible);
    return new Point(x, y);
  }

  private int getTabbedTextWidth(int lineStartOffset, VisualPosition pos) {
    if (pos.column == 0) return 0;

    int x = 0;
    int offset = lineStartOffset;
    CharSequence text = myDocument.getCharsNoThreadCheck();
    int textLength = myDocument.getTextLength();
    IterationState state = new IterationState(this, offset, false);
    int fontType = state.getMergedAttributes().getFontType();
    int spaceSize = EditorUtil.getSpaceWidth(fontType, this);

    int column = 0;
    outer:
    while (column < pos.column) {
      if (offset >= textLength) break;

      if (offset >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }

      FoldRegion region = state.getCurrentFold();

      if (region != null) {
        char[] placeholder = region.getPlaceholderText().toCharArray();
        for (char aPlaceholder : placeholder) {
          x += EditorUtil.charWidth(aPlaceholder, fontType, this);
          column++;
          if (column >= pos.column) break outer;
        }
        offset = region.getEndOffset();
      }
      else {
        char c = text.charAt(offset);
        if (c == '\n') {
          break;
        }
        if (c == '\t') {
          int prevX = x;
          x = EditorUtil.nextTabStop(x, this);
          column += (x - prevX) / spaceSize;
        }
        else {
          x += EditorUtil.charWidth(c, fontType, this);
          column++;
        }
        offset++;
      }
    }

    if (column != pos.column) {
      x += EditorUtil.getSpaceWidth(fontType, this) * (pos.column - column);
    }

    return x;
  }

  public int visibleLineNumberToYPosition(int lineNum) {
    if (lineNum < 0) throw new IndexOutOfBoundsException("Wrong line: " + lineNum);
    return lineNum * getLineHeight();
  }

  public void repaint(int startOffset, int endOffset) {
    if (!isShowing() || myScrollPane == null || myDocument.isInBulkUpdate()) {
      return;
    }

    assertIsDispatchThread();

    if (endOffset > myDocument.getTextLength()) {
      endOffset = myDocument.getTextLength();
    }
    if (startOffset < endOffset) {
      int startLine = myDocument.getLineNumber(startOffset);
      int endLine = myDocument.getLineNumber(endOffset);
      repaintLines(startLine, endLine);
    }
  }

  private boolean isShowing() {
    return myGutterComponent != null && myGutterComponent.isShowing();
  }

  private void repaintToScreenBotton(int startLine) {
    Rectangle visibleRect = getScrollingModel().getVisibleArea();
    int yStartLine = logicalLineToY(startLine);
    int yEndLine = visibleRect.y + visibleRect.height;

    myEditorComponent.repaintEditorComponent(visibleRect.x, yStartLine, visibleRect.x + visibleRect.width, yEndLine - yStartLine);
    myGutterComponent.repaint(0, yStartLine, myGutterComponent.getWidth(), yEndLine - yStartLine);
  }

  public void repaintLines(int startLine, int endLine) {
    if (!isShowing()) return;

    Rectangle visibleRect = getScrollingModel().getVisibleArea();
    int yStartLine = logicalLineToY(startLine);
    int yEndLine = logicalLineToY(endLine) + getLineHeight() + WAVE_HEIGHT;

    myEditorComponent.repaintEditorComponent(visibleRect.x, yStartLine, visibleRect.x + visibleRect.width, yEndLine - yStartLine);
    myGutterComponent.repaint(0, yStartLine, myGutterComponent.getWidth(), yEndLine - yStartLine);
  }

  private void beforeChangedUpdate(DocumentEvent e) {
    if (!myDocument.isInBulkUpdate()) {
      Rectangle viewRect = getScrollingModel().getVisibleArea();
      Point pos = visualPositionToXY(getCaretModel().getVisualPosition());
      myCaretUpdateVShift = pos.y - viewRect.y;
    }
    mySizeContainer.beforeChange(e);
  }

  private void changedUpdate(DocumentEvent e) {
    if (myScrollPane == null) return;

    stopOptimizedScrolling();
    mySelectionModel.removeBlockSelection();

    mySizeContainer.changedUpdate(e);
    validateSize();

    int startLine = calcLogicalLineNumber(e.getOffset());
    int endLine = calcLogicalLineNumber(e.getOffset() + e.getNewLength());

    boolean painted = false;
    if (myDocument.getTextLength() > 0) {
      int startDocLine = myDocument.getLineNumber(e.getOffset());
      int endDocLine = myDocument.getLineNumber(e.getOffset() + e.getNewLength());
      if (e.getOldLength() > e.getNewLength() || startDocLine != endDocLine) {
        updateGutterSize();
      }

      if (countLineFeeds(e.getOldFragment()) != countLineFeeds(e.getNewFragment())) {
        // Lines removed. Need to repaint till the end of the screen
        repaintToScreenBotton(startLine);
        painted = true;
      }
    }

    updateCaretCursor();
    if (!painted) {
      repaintLines(startLine, endLine);
    }

    if (!myDocument.isInBulkUpdate()) {
      Point caretLocation = visualPositionToXY(getCaretModel().getVisualPosition());
      int scrollOffset = caretLocation.y - myCaretUpdateVShift;
      getScrollingModel().scrollVertically(scrollOffset);
    }
  }

  private static int countLineFeeds(CharSequence c) {
    return StringUtil.countNewLines(c);
  }

  private void updateGutterSize() {
    if (myGutterSizeUpdater != null) return;
    myGutterSizeUpdater = new Runnable() {
      public void run() {
        if (!isDisposed()) {
          if (isShowing()) {
            myGutterComponent.updateSize();
            myGutterNeedsUpdate = false;
          }
          else {
            myGutterNeedsUpdate = true;
          }
        }
        myGutterSizeUpdater = null;
      }
    };

    SwingUtilities.invokeLater(myGutterSizeUpdater);
  }

  void validateSize() {
    Dimension dim = getPreferredSize();

    if (!dim.equals(myPreferredSize) && !myDocument.isInBulkUpdate()) {
      myPreferredSize = dim;

      stopOptimizedScrolling();
      int lineNum = Math.max(1, getDocument().getLineCount());
      myGutterComponent.setLineNumberAreaWidth(getFontMetrics(Font.PLAIN).stringWidth(Integer.toString(lineNum + 2)) + 6);

      myEditorComponent.setSize(dim);
      myEditorComponent.fireResized();

      myMarkupModel.repaint();
    }
  }

  void recalcSizeAndRepaint() {
    mySizeContainer.reset();
    validateSize();
    myEditorComponent.repaintEditorComponent();
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  public void addEditorMouseListener(@NotNull EditorMouseListener listener) {
    myMouseListeners.add(listener);
  }

  public void removeEditorMouseListener(@NotNull EditorMouseListener listener) {
    boolean success = myMouseListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    myMouseMotionListeners.add(listener);
  }

  public void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    boolean success = myMouseMotionListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public boolean isDisposed() {
    return isReleased;
  }

  void paint(Graphics g) {
    startOptimizedScrolling();

    if (myCursorUpdater != null) {
      myCursorUpdater.run();
      myCursorUpdater = null;
    }

    Rectangle clip = getClipBounds(g);

    if (clip == null) {
      return;
    }

    Rectangle viewRect = getScrollingModel().getVisibleArea();
    if (viewRect == null) {
      return;
    }

    if (isReleased) {
      g.setColor(new Color(128, 255, 128));
      g.fillRect(clip.x, clip.y, clip.width, clip.height);

      return;
    }

    paintBackgrounds(g, clip);
    paintRectangularSelection(g);
    paintRightMargin(g, clip);
    final MarkupModel docMarkup = myDocument.getMarkupModel(myProject);
    paintLineMarkersSeparators(g, clip, docMarkup);
    paintLineMarkersSeparators(g, clip, myMarkupModel);
    paintText(g, clip);
    paintSegmentHighlightersBorderAndAfterEndOfLine(g, clip);
    BorderEffect borderEffect = new BorderEffect(this, g);
    borderEffect.paintHighlighters(getHighlighter());
    borderEffect.paintHighlighters(docMarkup.getAllHighlighters());
    borderEffect.paintHighlighters(getMarkupModel().getAllHighlighters());
    paintCaretCursor(g);

    paintComposedTextDecoration((Graphics2D)g);
  }

  public void setHeaderComponent(JComponent header) {
    myHeaderPanel.removeAll();
    if (header != null) {
      myHeaderPanel.add(header);
    }

    myHeaderPanel.revalidate();
  }

  public boolean hasHeaderComponent() {
    return myHeaderPanel.getComponentCount() > 0;
  }

  @Nullable
  public JComponent getHeaderComponent() {
    if (hasHeaderComponent()) {
      return (JComponent)myHeaderPanel.getComponent(0);
    }
    return null;
  }

  public void setBackgroundColor(Color color) {
    myForcedBackground = color;
  }

  public void resetBackgourndColor() {
    myForcedBackground = null;
  }

  public Color getForegroundColor() {
    return myScheme.getDefaultForeground();
  }

  public Color getBackroundColor() {
    if (myForcedBackground != null) return myForcedBackground;

    return getBackgroundIgnoreForced();
  }

  private Color getBackgroundColor(final TextAttributes attributes) {
    final Color attrColor = attributes.getBackgroundColor();
    return attrColor == myScheme.getDefaultBackground() ? getBackroundColor() : attrColor;
  }

  private Color getBackgroundIgnoreForced() {
    Color color = myScheme.getDefaultBackground();
    if (myDocument.isWritable()) {
      return color;
    }
    Color readOnlyColor = myScheme.getColor(EditorColors.READONLY_BACKGROUND_COLOR);
    return readOnlyColor != null ? readOnlyColor : color;
  }

  private void paintComposedTextDecoration(Graphics2D g) {
    if (myInputMethodRequestsHandler != null && myInputMethodRequestsHandler.composedText != null) {
      VisualPosition visStart =
        offsetToVisualPosition(Math.min(myInputMethodRequestsHandler.composedTextStart, myDocument.getTextLength()));
      int y = visibleLineNumberToYPosition(visStart.line) + getLineHeight() - getDescent() + 1;
      Point p1 = visualPositionToXY(visStart);
      Point p2 =
        logicalPositionToXY(offsetToLogicalPosition(Math.min(myInputMethodRequestsHandler.composedTextEnd, myDocument.getTextLength())));

      Stroke saved = g.getStroke();
      BasicStroke dotted = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{0, 2, 0, 2}, 0);
      g.setStroke(dotted);
      UIUtil.drawLine(g, p1.x, y, p2.x, y);
      g.setStroke(saved);
    }
  }

  private static Rectangle getClipBounds(Graphics g) {
    return g.getClipBounds();
  }

  private void paintRightMargin(Graphics g, Rectangle clip) {
    Color rightMargin = myScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR);
    if (!mySettings.isRightMarginShown() || rightMargin == null) {
      return;
    }
    int x = mySettings.getRightMargin(myProject) * EditorUtil.getSpaceWidth(Font.PLAIN, this);
    if (x >= clip.x && x < clip.x + clip.width) {
      g.setColor(rightMargin);
      UIUtil.drawLine(g, x, clip.y, x, clip.y + clip.height);
    }
  }

  private void paintSegmentHighlightersBorderAndAfterEndOfLine(Graphics g, Rectangle clip) {
    if (myDocument.getLineCount() == 0) return;
    int startLineNumber = yPositionToVisibleLineNumber(clip.y);
    int endLineNumber = yPositionToVisibleLineNumber(clip.y + clip.height) + 1;

    final MarkupModel docMarkup = myDocument.getMarkupModel(myProject);
    RangeHighlighter[] segmentHighlighters = docMarkup.getAllHighlighters();
    for (RangeHighlighter segmentHighlighter : segmentHighlighters) {
      paintSegmentHighlighterAfterEndOfLine(g, (RangeHighlighterEx)segmentHighlighter, startLineNumber, endLineNumber);
    }

    segmentHighlighters = getMarkupModel().getAllHighlighters();
    for (RangeHighlighter segmentHighlighter : segmentHighlighters) {
      paintSegmentHighlighterAfterEndOfLine(g, (RangeHighlighterEx)segmentHighlighter, startLineNumber, endLineNumber);
    }
  }

  private void paintSegmentHighlighterAfterEndOfLine(Graphics g,
                                                     RangeHighlighterEx segmentHighlighter,
                                                     int startLineNumber,
                                                     int endLineNumber) {
    if (!segmentHighlighter.isValid()) {
      return;
    }
    if (segmentHighlighter.isAfterEndOfLine()) {
      int startOffset = segmentHighlighter.getStartOffset();
      int visibleStartLine = offsetToVisualPosition(startOffset).line;

      if (!getFoldingModel().isOffsetCollapsed(startOffset)) {
        if (visibleStartLine >= startLineNumber && visibleStartLine <= endLineNumber) {
          int logStartLine = offsetToLogicalPosition(startOffset).line;
          LogicalPosition logPosition = offsetToLogicalPosition(myDocument.getLineEndOffset(logStartLine));
          Point end = logicalPositionToXY(logPosition);
          int charWidth = EditorUtil.getSpaceWidth(Font.PLAIN, this);
          int lineHeight = getLineHeight();
          TextAttributes attributes = segmentHighlighter.getTextAttributes();
          if (attributes != null && getBackgroundColor(attributes) != null) {
            g.setColor(getBackgroundColor(attributes));
            g.fillRect(end.x, end.y, charWidth, lineHeight);
          }
          if (attributes != null && attributes.getEffectColor() != null) {
            int y = visibleLineNumberToYPosition(visibleStartLine) + getLineHeight() - getDescent() + 1;
            g.setColor(attributes.getEffectColor());
            if (attributes.getEffectType() == EffectType.WAVE_UNDERSCORE) {
              drawWave(g, end.x, end.x + charWidth - 1, y);
            }
            else if (attributes.getEffectType() != EffectType.BOXED) {
              UIUtil.drawLine(g, end.x, y, end.x + charWidth - 1, y);
            }
          }
        }
      }
    }
  }

  public int getMaxWidthInRange(int startOffset, int endOffset) {
    int width = 0;
    VisualPosition start = offsetToVisualPosition(startOffset);
    VisualPosition end = offsetToVisualPosition(endOffset);

    for (int i = start.line; i <= end.line; i++) {
      int lastColumn = EditorUtil.getLastVisualLineColumnNumber(this, i) + 1;
      int lineWidth = visualPositionToXY(new VisualPosition(i, lastColumn)).x;

      if (lineWidth > width) {
        width = lineWidth;
      }
    }

    return width;
  }

  private void paintBackgrounds(Graphics g, Rectangle clip) {
    Color defaultBackground = getBackroundColor();
    g.setColor(defaultBackground);
    g.fillRect(clip.x, clip.y, clip.width, clip.height);

    int lineHeight = getLineHeight();

    int visibleLineNumber = clip.y / lineHeight;

    int startLineNumber = xyToLogicalPosition(new Point(0, clip.y)).line;

    if (startLineNumber >= myDocument.getLineCount() || startLineNumber < 0) {
      return;
    }

    int start = myDocument.getLineStartOffset(startLineNumber);

    IterationState iterationState = new IterationState(this, start, paintSelection());

    LineIterator lIterator = createLineIterator();
    lIterator.start(start);
    if (lIterator.atEnd()) {
      return;
    }

    myLastBackgroundPosition = null;
    myLastBackgroundColor = null;

    TextAttributes attributes = iterationState.getMergedAttributes();
    Color backColor = getBackgroundColor(attributes);
    Point position = new Point(0, visibleLineNumber * lineHeight);
    int fontType = attributes.getFontType();
    CharSequence text = myDocument.getCharsNoThreadCheck();
    int lastLineIndex = Math.max(0, myDocument.getLineCount() - 1);
    while (!iterationState.atEnd() && !lIterator.atEnd()) {
      int hEnd = iterationState.getEndOffset();
      int lEnd = lIterator.getEnd();

      if (hEnd >= lEnd) {
        FoldRegion collapsedFolderAt = myFoldingModel.getCollapsedRegionAtOffset(start);
        if (collapsedFolderAt == null) {
          position.x = drawBackground(g, backColor, text.subSequence(start, lEnd - lIterator.getSeparatorLength()), position, fontType,
                                      defaultBackground, clip);

          if (lIterator.getLineNumber() < lastLineIndex) {
            if (backColor != null && !backColor.equals(defaultBackground)) {
              g.setColor(backColor);
              g.fillRect(position.x, position.y, clip.x + clip.width - position.x, lineHeight);
            }
          }
          else {
            paintAfterFileEndBackground(iterationState, g, position, clip, lineHeight, defaultBackground);
            break;
          }

          position.x = 0;
          if (position.y > clip.y + clip.height) break;
          position.y += lineHeight;
          start = lEnd;
        }

        lIterator.advance();
      }
      else {
        FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
        if (collapsedFolderAt != null) {
          position.x = drawBackground(g, backColor, collapsedFolderAt.getPlaceholderText(), position, fontType, defaultBackground, clip);
        }
        else {
          if (hEnd > lEnd - lIterator.getSeparatorLength()) {
            position.x = drawBackground(g, backColor, text.subSequence(start, lEnd - lIterator.getSeparatorLength()), position, fontType,
                                        defaultBackground, clip);
          }
          else {
            position.x = drawBackground(g, backColor, text.subSequence(start, hEnd), position, fontType, defaultBackground, clip);
          }
        }

        iterationState.advance();
        attributes = iterationState.getMergedAttributes();
        backColor = getBackgroundColor(attributes);
        fontType = attributes.getFontType();
        start = iterationState.getStartOffset();
      }
    }

    flushBackground(g, clip);

    if (lIterator.getLineNumber() >= lastLineIndex && position.y <= clip.y + clip.height) {
      paintAfterFileEndBackground(iterationState, g, position, clip, lineHeight, defaultBackground);
    }
  }

  private void paintRectangularSelection(Graphics g) {
    final SelectionModel model = getSelectionModel();
    if (!model.hasBlockSelection()) return;
    final LogicalPosition blockStart = model.getBlockStart();
    final LogicalPosition blockEnd = model.getBlockEnd();
    assert blockStart != null;
    assert blockEnd != null;

    final Point start = logicalPositionToXY(blockStart);
    final Point end = logicalPositionToXY(blockEnd);
    g.setColor(myScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
    final int y;
    final int height;
    if (start.y <= end.y) {
      y = start.y;
      height = end.y - y + getLineHeight();
    }
    else {
      y = end.y;
      height = start.y - end.y + getLineHeight();
    }
    final int x = Math.min(start.x, end.x);
    final int width = Math.max(2, Math.abs(end.x - start.x));
    g.fillRect(x, y, width, height);
  }

  private static void paintAfterFileEndBackground(IterationState iterationState,
                                                  Graphics g,
                                                  Point position,
                                                  Rectangle clip,
                                                  int lineHeight,
                                                  final Color defaultBackground) {
    Color backColor = iterationState.getPastFileEndBackground();
    if (backColor != null && !backColor.equals(defaultBackground)) {
      g.setColor(backColor);
      g.fillRect(position.x, position.y, clip.x + clip.width - position.x, lineHeight);
    }
  }

  private int drawBackground(Graphics g,
                             Color backColor,
                             CharSequence text,
                             Point position,
                             int fontType,
                             Color defaultBackground,
                             Rectangle clip) {
    int w = getTextSegmentWidth(text, position.x, fontType, clip);

    if (backColor != null && !backColor.equals(defaultBackground) && clip.intersects(position.x, position.y, w, getLineHeight())) {
      if (backColor.equals(myLastBackgroundColor) && myLastBackgroundPosition.y == position.y &&
          myLastBackgroundPosition.x + myLastBackgroundWidth == position.x) {
        myLastBackgroundWidth += w;
      }
      else {
        flushBackground(g, clip);
        myLastBackgroundColor = backColor;
        myLastBackgroundPosition = new Point(position);
        myLastBackgroundWidth = w;
      }
    }

    return position.x + w;
  }

  private void flushBackground(Graphics g, final Rectangle clip) {
    if (myLastBackgroundColor != null) {
      final Point position = myLastBackgroundPosition;
      final int w = myLastBackgroundWidth;
      final int height = getLineHeight();
      if (clip.intersects(position.x, position.y, w, height)) {
        g.setColor(myLastBackgroundColor);
        g.fillRect(position.x, position.y, w, height);
      }
      myLastBackgroundColor = null;
    }
  }

  private LineIterator createLineIterator() {
    return myDocument.createLineIterator();
  }

  private void paintText(Graphics g, Rectangle clip) {
    myLastCache = null;
    final int plainSpaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, this);
    final int boldSpaceWidth = EditorUtil.getSpaceWidth(Font.BOLD, this);
    final int italicSpaceWidth = EditorUtil.getSpaceWidth(Font.ITALIC, this);
    final int boldItalicSpaceWidth = EditorUtil.getSpaceWidth(Font.BOLD | Font.ITALIC, this);
    mySpacesHaveSameWidth =
      plainSpaceWidth == boldSpaceWidth && plainSpaceWidth == italicSpaceWidth && plainSpaceWidth == boldItalicSpaceWidth;

    int lineHeight = getLineHeight();

    int visibleLineNumber = clip.y / lineHeight;

    int startLineNumber = xyToLogicalPosition(new Point(0, clip.y)).line;

    if (startLineNumber >= myDocument.getLineCount() || startLineNumber < 0) {
      return;
    }

    int start = myDocument.getLineStartOffset(startLineNumber);

    IterationState iterationState = new IterationState(this, start, paintSelection());

    LineIterator lIterator = createLineIterator();
    lIterator.start(start);
    if (lIterator.atEnd()) {
      return;
    }

    TextAttributes attributes = iterationState.getMergedAttributes();
    Color currentColor = attributes.getForegroundColor();
    if (currentColor == null) {
      currentColor = getForegroundColor();
    }
    Color effectColor = attributes.getEffectColor();
    EffectType effectType = attributes.getEffectType();
    int fontType = attributes.getFontType();
    myCurrentFontType = null;
    g.setColor(currentColor);
    Point position = new Point(0, visibleLineNumber * lineHeight);
    final char[] chars = myDocument.getRawChars();
    while (!iterationState.atEnd() && !lIterator.atEnd()) {
      int hEnd = iterationState.getEndOffset();
      int lEnd = lIterator.getEnd();
      if (hEnd >= lEnd) {
        FoldRegion collapsedFolderAt = myFoldingModel.getCollapsedRegionAtOffset(start);
        if (collapsedFolderAt == null) {
          drawString(g, chars, start, lEnd - lIterator.getSeparatorLength(), position, clip, effectColor, effectType, fontType,
                     currentColor);
          position.x = 0;
          if (position.y > clip.y + clip.height) break;
          position.y += lineHeight;
          start = lEnd;
        }

//        myBorderEffect.eolReached(g, this);
        lIterator.advance();
      }
      else {
        FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
        if (collapsedFolderAt != null) {
          int foldingXStart = position.x;
          position.x =
            drawString(g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType, fontType, currentColor);
          BorderEffect.paintFoldedEffect(g, foldingXStart, position.y, position.x, getLineHeight(), effectColor, effectType);

        }
        else {
          if (hEnd > lEnd - lIterator.getSeparatorLength()) {
            position.x = drawString(g, chars, start, lEnd - lIterator.getSeparatorLength(), position, clip, effectColor, effectType,
                                    fontType, currentColor);
          }
          else {
            position.x = drawString(g, chars, start, hEnd, position, clip, effectColor, effectType, fontType, currentColor);
          }
        }

        iterationState.advance();
        attributes = iterationState.getMergedAttributes();

        currentColor = attributes.getForegroundColor();
        if (currentColor == null) {
          currentColor = getForegroundColor();
        }

        effectColor = attributes.getEffectColor();
        effectType = attributes.getEffectType();
        fontType = attributes.getFontType();

        start = iterationState.getStartOffset();
      }
    }

    FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
    if (collapsedFolderAt != null) {
      int foldingXStart = position.x;
      int foldingXEnd =
        drawString(g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType, fontType, currentColor);
      BorderEffect.paintFoldedEffect(g, foldingXStart, position.y, foldingXEnd, getLineHeight(), effectColor, effectType);
//      myBorderEffect.collapsedFolderReached(g, this);
    }

    flushCachedChars(g);
  }

  private boolean paintSelection() {
    return !isOneLineMode() || IJSwingUtilities.hasFocus(getContentComponent());
  }

  private class CachedFontContent {
    final char[][] data = new char[CACHED_CHARS_BUFFER_SIZE][];
    final int[] starts = new int[CACHED_CHARS_BUFFER_SIZE];
    final int[] ends = new int[CACHED_CHARS_BUFFER_SIZE];
    final int[] x = new int[CACHED_CHARS_BUFFER_SIZE];
    final int[] y = new int[CACHED_CHARS_BUFFER_SIZE];
    final Color[] color = new Color[CACHED_CHARS_BUFFER_SIZE];

    int myCount = 0;
    final FontInfo myFontType;

    private char[] myLastData;

    private CachedFontContent(FontInfo fontInfo) {
      myFontType = fontInfo;
    }

    private void flushContent(Graphics g) {
      if (myCount != 0) {
        if (myCurrentFontType != myFontType) {
          myCurrentFontType = myFontType;
          g.setFont(myFontType.getFont());
        }
        Color currentColor = null;
        for (int i = 0; i < myCount; i++) {
          if (!Comparing.equal(color[i], currentColor)) {
            currentColor = color[i];
            g.setColor(currentColor != null ? currentColor : Color.black);
          }

          drawChars(g, data[i], starts[i], ends[i], x[i], y[i]);
          color[i] = null;
          data[i] = null;
        }

        myCount = 0;
        myLastData = null;
      }
    }

    private void addContent(Graphics g, char[] _data, int _start, int _end, int _x, int _y, Color _color) {
      final int count = myCount;
      if (count > 0) {
        final int lastCount = count - 1;
        final Color lastColor = color[lastCount];
        if (_data == myLastData && _start == ends[lastCount] && (_color == null || lastColor == null || _color == lastColor)) {
          ends[lastCount] = _end;
          if (lastColor == null) color[lastCount] = _color;
          return;
        }
      }

      myLastData = _data;
      data[count] = _data;
      x[count] = _x;
      y[count] = _y;
      starts[count] = _start;
      ends[count] = _end;
      color[count] = _color;

      myCount++;
      if (count >= CACHED_CHARS_BUFFER_SIZE - 1) {
        flushContent(g);
      }
    }
  }

  private void flushCachedChars(Graphics g) {
    for (CachedFontContent cache : myFontCache) {
      cache.flushContent(g);
    }
    myLastCache = null;
  }

  private void paintCaretCursor(Graphics g) {
    myCaretCursor.paint(g);
  }

  private void paintLineMarkersSeparators(Graphics g, Rectangle clip, MarkupModel markupModel) {
    if (markupModel == null) return;
    RangeHighlighter[] lineMarkers = markupModel.getAllHighlighters();
    for (RangeHighlighter lineMarker : lineMarkers) {
      paintLineMarkerSeparator(lineMarker, clip, g);
    }
  }

  private void paintLineMarkerSeparator(RangeHighlighter marker, Rectangle clip, Graphics g) {
    if (!marker.isValid()) {
      return;
    }
    Color separatorColor = marker.getLineSeparatorColor();
    if (separatorColor != null) {
      int lineNumber = marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP ? marker.getDocument()
        .getLineNumber(marker.getStartOffset()) : marker.getDocument().getLineNumber(marker.getEndOffset());
      if (lineNumber < 0 || lineNumber >= myDocument.getLineCount()) {
        return;
      }

      int y = visibleLineNumberToYPosition(logicalToVisualPosition(new LogicalPosition(lineNumber, 0)).line);
      if (marker.getLineSeparatorPlacement() != SeparatorPlacement.TOP) {
        y += getLineHeight();
      }

      if (y < clip.y || y > clip.y + clip.height) return;

      int endShift = clip.x + clip.width;
      g.setColor(separatorColor);

      if (mySettings.isRightMarginShown() && myScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR) != null) {
        endShift = Math.min(endShift, mySettings.getRightMargin(myProject) * EditorUtil.getSpaceWidth(Font.PLAIN, this));
      }

      UIUtil.drawLine(g, 0, y - 1, endShift, y - 1);
    }
  }

  private int drawString(Graphics g,
                         final char[] text,
                         int start,
                         int end,
                         Point position,
                         Rectangle clip,
                         Color effectColor,
                         EffectType effectType,
                         int fontType,
                         Color fontColor) {
    if (start >= end) return position.x;

    boolean isInClip = getLineHeight() + position.y >= clip.y && position.y <= clip.y + clip.height;

    if (!isInClip) return position.x;

    int y = getLineHeight() - getDescent() + position.y;
    int x = position.x;
    return drawTabbedString(g, text, start, end, x, y, effectColor, effectType, fontType, fontColor, clip);
  }

  private int drawString(Graphics g,
                         String text,
                         Point position,
                         Rectangle clip,
                         Color effectColor,
                         EffectType effectType,
                         int fontType,
                         Color fontColor) {
    boolean isInClip = getLineHeight() + position.y >= clip.y && position.y <= clip.y + clip.height;

    if (!isInClip) return position.x;

    int y = getLineHeight() - getDescent() + position.y;
    int x = position.x;

    return drawTabbedString(g, text.toCharArray(), 0, text.length(), x, y, effectColor, effectType, fontType, fontColor, clip);
  }

  private int drawTabbedString(Graphics g,
                               char[] text,
                               int start,
                               int end,
                               int x,
                               int y,
                               Color effectColor,
                               EffectType effectType,
                               int fontType,
                               Color fontColor,
                               final Rectangle clip) {
    int xStart = x;

    for (int i = start; i < end; i++) {
      if (text[i] != '\t') continue;

      x = drawTablessString(text, start, i, g, x, y, fontType, fontColor, clip);

      int x1 = EditorUtil.nextTabStop(x, this);
      drawTabPlacer(g, y, x, x1);
      x = x1;
      start = i + 1;
    }

    x = drawTablessString(text, start, end, g, x, y, fontType, fontColor, clip);

    if (effectColor != null) {
      final Color savedColor = g.getColor();

//      myBorderEffect.flushIfCantProlong(g, this, effectType, effectColor);
      int xEnd = x;
      if (xStart < clip.x && xEnd < clip.x || xStart > clip.x + clip.width && xEnd > clip.x + clip.width) {
        return x;
      }

      if (xEnd > clip.x + clip.width) {
        xEnd = clip.x + clip.width;
      }
      if (xStart < clip.x) {
        xStart = clip.x;
      }

      if (effectType == EffectType.LINE_UNDERSCORE) {
        g.setColor(effectColor);
        UIUtil.drawLine(g, xStart, y + 1, xEnd, y + 1);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        g.setColor(effectColor);
        UIUtil.drawLine(g, xStart, y, xEnd, y);
        UIUtil.drawLine(g, xStart, y + 1, xEnd, y + 1);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.STRIKEOUT) {
        g.setColor(effectColor);
        int y1 = y - getCharHeight() / 2;
        UIUtil.drawLine(g, xStart, y1, xEnd, y1);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        g.setColor(effectColor);
        drawWave(g, xStart, xEnd, y + 1);
        g.setColor(savedColor);
      }
    }

    return x;
  }

  private int drawTablessString(final char[] text,
                                int start,
                                final int end,
                                final Graphics g,
                                int x,
                                final int y,
                                final int fontType,
                                final Color fontColor,
                                final Rectangle clip) {
    int endX = x;
    if (start < end) {
      FontInfo font = EditorUtil.fontForChar(text[start], fontType, this);
      for (int j = start; j < end; j++) {
        final char c = text[j];
        FontInfo newFont = EditorUtil.fontForChar(c, fontType, this);
        if (font != newFont || endX > clip.x + clip.width) {
          if (!(x < clip.x && endX < clip.x || x > clip.x + clip.width && endX > clip.x + clip.width)) {
            drawCharsCached(g, text, start, j, x, y, fontType, fontColor);
          }
          start = j;
          x = endX;
          font = newFont;
        }
        if (x < clip.x && endX < clip.x) {
          start = j;
          x = endX;
          font = newFont;
        }
        else if (x > clip.x + clip.width) {
          return endX;
        }
        endX += font.charWidth(c, myEditorComponent);
      }

      if (!(x < clip.x && endX < clip.x || x > clip.x + clip.width && endX > clip.x + clip.width)) {
        drawCharsCached(g, text, start, end, x, y, fontType, fontColor);
      }
    }

    return endX;
  }

  private void drawTabPlacer(Graphics g, int y, int start, int stop) {
    if (mySettings.isWhitespacesShown()) {
      stop -= g.getFontMetrics().charWidth(' ') / 2;
      Color oldColor = g.getColor();
      g.setColor(myScheme.getColor(EditorColors.WHITESPACES_COLOR));
      final int charHeight = getCharHeight();
      final int halfCharHeight = charHeight / 2;
      int mid = y - halfCharHeight;
      int top = y - charHeight;
      UIUtil.drawLine(g, start, mid, stop, mid);
      UIUtil.drawLine(g, stop, y, stop, top);
      g.fillPolygon(new int[]{stop - halfCharHeight, stop - halfCharHeight, stop}, new int[]{y, y - charHeight, y - halfCharHeight}, 3);
      g.setColor(oldColor);
    }
  }

  private void drawCharsCached(Graphics g, char[] data, int start, int end, int x, int y, int fontType, Color color) {
    if (mySpacesHaveSameWidth && myLastCache != null && spacesOnly(data, start, end)) {
      myLastCache.addContent(g, data, start, end, x, y, null);
    }
    else {
      FontInfo fnt = EditorUtil.fontForChar(data[start], fontType, this);
      CachedFontContent cache = null;
      for (CachedFontContent fontCache : myFontCache) {
        if (fontCache.myFontType == fnt) {
          cache = fontCache;
          break;
        }
      }
      if (cache == null) {
        cache = new CachedFontContent(fnt);
        myFontCache.add(cache);
      }

      myLastCache = cache;
      cache.addContent(g, data, start, end, x, y, color);
    }
  }

  private static boolean spacesOnly(char[] chars, int start, int end) {
    for (int i = start; i < end; i++) {
      if (chars[i] != ' ') return false;
    }
    return true;
  }

  private void drawChars(Graphics g, char[] data, int start, int end, int x, int y) {
    g.drawChars(data, start, end - start, x, y);

    if (mySettings.isWhitespacesShown()) {
      Color oldColor = g.getColor();
      g.setColor(myScheme.getColor(EditorColors.WHITESPACES_COLOR));
      final FontMetrics metrics = g.getFontMetrics();
      int halfSpaceWidth = metrics.charWidth(' ') / 2;
      for (int i = start; i < end; i++) {
        if (data[i] == ' ') {
          g.fillRect(x + halfSpaceWidth, y, 1, 1);
        }
        x += metrics.charWidth(data[i]);
      }
      g.setColor(oldColor);
    }
  }

  private static final int WAVE_HEIGHT = 2;
  private static final int WAVE_SEGMENT_LENGTH = 4;

  private static void drawWave(Graphics g, int xStart, int xEnd, int y) {
    int startSegment = xStart / WAVE_SEGMENT_LENGTH;
    int endSegment = xEnd / WAVE_SEGMENT_LENGTH;
    for (int i = startSegment; i < endSegment; i++) {
      drawWaveSegment(g, WAVE_SEGMENT_LENGTH * i, y);
    }

    int x = WAVE_SEGMENT_LENGTH * endSegment;
    UIUtil.drawLine(g, x, y + WAVE_HEIGHT, x + WAVE_SEGMENT_LENGTH / 2, y);
  }

  private static void drawWaveSegment(Graphics g, int x, int y) {
    UIUtil.drawLine(g, x, y + WAVE_HEIGHT, x + WAVE_SEGMENT_LENGTH / 2, y);
    UIUtil.drawLine(g, x + WAVE_SEGMENT_LENGTH / 2, y, x + WAVE_SEGMENT_LENGTH, y + WAVE_HEIGHT);
  }

  private int getTextSegmentWidth(CharSequence text, int xStart, int fontType, Rectangle clip) {
    int x = xStart;

    final int textLength = text.length();
    for (int i = 0; i < textLength && xStart < clip.x + clip.width; i++) {
      if (text.charAt(i) == '\t') {
        x = EditorUtil.nextTabStop(x, this);
      }
      else {
        x += EditorUtil.charWidth(text.charAt(i), fontType, this);
      }
      if (x > clip.x + clip.width) {
        break;
      }
    }
    return x - xStart;
  }

  public int getLineHeight() {
    if (myLineHeight != -1) return myLineHeight;

    assertReadAccess();

    FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
    myLineHeight = (int)(fontMetrics.getHeight() * (isOneLineMode() ? 1 : myScheme.getLineSpacing()));
    if (myLineHeight == 0) {
      myLineHeight = fontMetrics.getHeight();
      if (myLineHeight == 0) {
        myLineHeight = 12;
      }
    }

    return myLineHeight;
  }

  int getDescent() {
    if (myDescent != -1) {
      return myDescent;
    }
    FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
    myDescent = fontMetrics.getDescent();
    return myDescent;
  }

  FontMetrics getFontMetrics(int fontType) {
    if (myPlainFontMetrics == null) {
      assertIsDispatchThread();
      myPlainFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      myBoldFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.BOLD));
      myItalicFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.ITALIC));
      myBoldItalicFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.BOLD_ITALIC));
    }

    if (fontType == Font.PLAIN) return myPlainFontMetrics;
    if (fontType == Font.BOLD) return myBoldFontMetrics;
    if (fontType == Font.ITALIC) return myItalicFontMetrics;
    if (fontType == Font.BOLD + Font.ITALIC) return myBoldItalicFontMetrics;

    LOG.assertTrue(false, "Unknown font type: " + fontType);

    return myPlainFontMetrics;
  }

  private int getCharHeight() {
    if (myCharHeight == -1) {
      assertIsDispatchThread();
      FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      myCharHeight = fontMetrics.charWidth('a');
    }
    return myCharHeight;
  }

  public Dimension getPreferredSize() {
    if (ourIsUnitTestMode && getUserData(DO_DOCUMENT_UPDATE_TEST) == null) {
      return new Dimension(1, 1);
    }

    final Dimension draft = getSizeWithoutCaret();
    final int additionalSpace = mySettings.getAdditionalColumnsCount() * EditorUtil.getSpaceWidth(Font.PLAIN, this);

    if (!myDocument.isInBulkUpdate()) {
      int caretX = visualPositionToXY(getCaretModel().getVisualPosition()).x;
      draft.width = Math.max(caretX, draft.width) + additionalSpace;
    }
    else {
      draft.width += additionalSpace;
    }
    return draft;
  }

  private Dimension getSizeWithoutCaret() {
    Dimension size = mySizeContainer.getContentSize();
    if (isOneLineMode()) return new Dimension(size.width, getLineHeight());
    if (mySettings.isAdditionalPageAtBottom()) {
      int lineHeight = getLineHeight();
      return new Dimension(size.width, size.height + Math.max(getScrollingModel().getVisibleArea().height - 2 * lineHeight, lineHeight));
    }

    return getContentSize();
  }

  public Dimension getContentSize() {
    Dimension size = mySizeContainer.getContentSize();
    return new Dimension(size.width, size.height + mySettings.getAdditionalLinesCount() * getLineHeight());
  }

  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    assertReadAccess();
    if (myDocument.getLineCount() == 0) return 0;

    if (pos.line < 0) throw new IndexOutOfBoundsException("Wrong line: " + pos.line);
    if (pos.column < 0) throw new IndexOutOfBoundsException("Wrong column:" + pos.column);

    if (pos.line >= myDocument.getLineCount()) {
      return myDocument.getTextLength();
    }

    int start = myDocument.getLineStartOffset(pos.line);
    int end = myDocument.getLineEndOffset(pos.line);

    CharSequence text = myDocument.getCharsNoThreadCheck();

    if (pos.column == 0) return start;
    return EditorUtil.calcOffset(this, text, start, end, pos.column, EditorUtil.getTabSize(this));
  }

  public void setLastColumnNumber(int val) {
    assertIsDispatchThread();
    myLastColumnNumber = val;
  }

  public int getLastColumnNumber() {
    assertReadAccess();
    return myLastColumnNumber;
  }

  int getVisibleLineCount() {
    int line = getDocument().getLineCount();
    line -= myFoldingModel.getFoldedLinesCountBefore(getDocument().getTextLength() + 1);
    return line;
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos) {
    assertReadAccess();
    if (!myFoldingModel.isFoldingEnabled()) return new VisualPosition(logicalPos.line, logicalPos.column);

    int offset = logicalPositionToOffset(logicalPos);

    FoldRegion outermostCollapsed = myFoldingModel.getCollapsedRegionAtOffset(offset);
    if (outermostCollapsed != null && offset > outermostCollapsed.getStartOffset()) {
      if (offset < getDocument().getTextLength()) {
        offset = outermostCollapsed.getStartOffset();
        LogicalPosition foldStart = offsetToLogicalPosition(offset);
        return logicalToVisualPosition(foldStart);
      }
      else {
        offset = outermostCollapsed.getEndOffset() + 3;  // WTF?
      }
    }

    int line = logicalPos.line;
    int column = logicalPos.column;

    line -= myFoldingModel.getFoldedLinesCountBefore(offset);

    FoldRegion[] toplevel = myFoldingModel.fetchTopLevel();
    for (int idx = myFoldingModel.getLastTopLevelIndexBefore(offset); idx >= 0; idx--) {
      FoldRegion region = toplevel[idx];
      if (region.isValid()) {
        if (region.getDocument().getLineNumber(region.getEndOffset()) == logicalPos.line && region.getEndOffset() <= offset) {
          LogicalPosition foldStart = offsetToLogicalPosition(region.getStartOffset());
          LogicalPosition foldEnd = offsetToLogicalPosition(region.getEndOffset());
          column += foldStart.column + region.getPlaceholderText().length() - foldEnd.column;
          offset = region.getStartOffset();
          logicalPos = foldStart;
        }
        else {
          break;
        }
      }
    }

    LOG.assertTrue(line >= 0);

    return new VisualPosition(line, Math.max(0, column));
  }

  @Nullable
  private FoldRegion getLastCollapsedBeforePosition(VisualPosition visual) {
    FoldRegion[] topLevelCollapsed = myFoldingModel.fetchTopLevel();

    if (topLevelCollapsed == null) return null;

    int start = 0;
    int end = topLevelCollapsed.length - 1;
    int i = 0;

    while (start <= end) {
      i = (start + end) / 2;
      FoldRegion region = topLevelCollapsed[i];
      LogicalPosition logFoldEnd = offsetToLogicalPosition(region.getEndOffset() - 1);
      VisualPosition visFoldEnd = logicalToVisualPosition(logFoldEnd);
      if (visFoldEnd.line < visual.line) {
        start = i + 1;
      }
      else {
        if (visFoldEnd.line > visual.line) {
          end = i - 1;
        }
        else {
          if (visFoldEnd.column < visual.column) {
            start = i + 1;
          }
          else {
            if (visFoldEnd.column > visual.column) {
              end = i - 1;
            }
            else {
              i--;
              break;
            }
          }
        }
      }
    }

    while (i >= 0 && i < topLevelCollapsed.length) {
      if (topLevelCollapsed[i].isValid()) break;
      i--;
    }

    if (i >= 0 && i < topLevelCollapsed.length) {
      FoldRegion region = topLevelCollapsed[i];
      LogicalPosition logFoldEnd = offsetToLogicalPosition(region.getEndOffset() - 1);
      VisualPosition visFoldEnd = logicalToVisualPosition(logFoldEnd);
      if (visFoldEnd.line > visual.line || visFoldEnd.line == visual.line && visFoldEnd.column > visual.column) {
        i--;
        if (i >= 0) {
          return topLevelCollapsed[i];
        }
        else {
          return null;
        }
      }
      return region;
    }

    return null;
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos) {
    assertReadAccess();
    if (!myFoldingModel.isFoldingEnabled()) return new LogicalPosition(visiblePos.line, visiblePos.column);

    int line = visiblePos.line;
    int column = visiblePos.column;

    FoldRegion lastCollapsedBefore = getLastCollapsedBeforePosition(visiblePos);

    if (lastCollapsedBefore != null) {
      LogicalPosition logFoldEnd = offsetToLogicalPosition(lastCollapsedBefore.getEndOffset());
      VisualPosition visFoldEnd = logicalToVisualPosition(logFoldEnd);

      line = logFoldEnd.line + (visiblePos.line - visFoldEnd.line);
      if (visFoldEnd.line == visiblePos.line) {
        if (visiblePos.column >= visFoldEnd.column) {
          column = logFoldEnd.column + (visiblePos.column - visFoldEnd.column);
        }
        else {
          return offsetToLogicalPosition(lastCollapsedBefore.getStartOffset());
        }
      }
    }

    if (column < 0) column = 0;

    return new LogicalPosition(line, column);
  }

  private int calcLogicalLineNumber(int offset) {
    int textLength = myDocument.getTextLength();
    if (textLength == 0) return 0;

    if (offset > textLength || offset < 0) {
      throw new IndexOutOfBoundsException("Wrong offset: " + offset + " textLength: " + textLength);
    }

    int lineIndex = myDocument.getLineNumber(offset);

    LOG.assertTrue(lineIndex >= 0 && lineIndex < myDocument.getLineCount());

    return lineIndex;
  }

  private int calcColumnNumber(int offset, int lineIndex) {
    if (myDocument.getTextLength() == 0) return 0;

    CharSequence text = myDocument.getCharsSequence();
    int start = myDocument.getLineStartOffset(lineIndex);
    if (start == offset) return 0;
    return EditorUtil.calcColumnNumber(this, text, start, offset, EditorUtil.getTabSize(this));
  }

  private void moveCaretToScreenPos(int x, int y) {
    if (x < 0) {
      x = 0;
    }

    LogicalPosition pos = xyToLogicalPosition(new Point(x, y));

    int columnNumber = pos.column;
    int lineNumber = pos.line;

    if (lineNumber >= myDocument.getLineCount()) {
      lineNumber = myDocument.getLineCount() - 1;
    }
    if (!mySettings.isVirtualSpace()) {
      if (lineNumber >= 0) {
        int lineEndOffset = myDocument.getLineEndOffset(lineNumber);
        int lineEndColumnNumber = calcColumnNumber(lineEndOffset, lineNumber);
        if (columnNumber > lineEndColumnNumber) {
          columnNumber = lineEndColumnNumber;
        }
      }
    }
    if (lineNumber < 0) {
      lineNumber = 0;
      columnNumber = 0;
    }
    if (!mySettings.isCaretInsideTabs()) {
      int offset = logicalPositionToOffset(new LogicalPosition(lineNumber, columnNumber));
      CharSequence text = myDocument.getCharsSequence();
      if (offset >= 0 && offset < myDocument.getTextLength()) {
        if (text.charAt(offset) == '\t') {
          columnNumber = calcColumnNumber(offset, lineNumber);
        }
      }
    }
    LogicalPosition pos1 = new LogicalPosition(lineNumber, columnNumber);
    getCaretModel().moveToLogicalPosition(pos1);
  }

  private boolean checkIgnore(MouseEvent e, boolean isFinalCheck) {
    if (!myIgnoreMouseEventsConsecutiveToInitial) {
      myInitialMouseEvent = null;
      return false;
    }

    if (e.getComponent() != myInitialMouseEvent.getComponent() || !e.getPoint().equals(myInitialMouseEvent.getPoint())) {
      myIgnoreMouseEventsConsecutiveToInitial = false;
      myInitialMouseEvent = null;
      return false;
    }

    if (isFinalCheck) {
      myIgnoreMouseEventsConsecutiveToInitial = false;
      myInitialMouseEvent = null;
    }

    e.consume();

    return true;
  }

  private void processMouseReleased(MouseEvent e) {
    if (checkIgnore(e, true)) return;

    if (e.getSource() == myGutterComponent) {
      myGutterComponent.mouseReleased(e);
    }

    if (getMouseEventArea(e) != EditorMouseEventArea.EDITING_AREA || e.getY() < 0 || e.getX() < 0) {
      return;
    }

//    if (myMousePressedInsideSelection) getSelectionModel().removeSelection();
    final FoldRegion region = ((FoldingModelEx)getFoldingModel()).getFoldingPlaceholderAt(e.getPoint());
    if (e.getX() >= 0 && e.getY() >= 0 && region != null && region == myMouseSelectedRegion) {
      getFoldingModel().runBatchFoldingOperation(new Runnable() {
        public void run() {
          myFoldingModel.flushCaretShift();
          region.setExpanded(true);
        }
      });
    }

    if (myMousePressedEvent != null && myMousePressedEvent.getClickCount() == 1 && myMousePressedInsideSelection) {
      getSelectionModel().removeSelection();
    }
  }

  public DataContext getDataContext() {
    return getProjectAwareDataContext(DataManager.getInstance().getDataContext(getContentComponent()));
  }

  private DataContext getProjectAwareDataContext(final DataContext original) {
    if (PlatformDataKeys.PROJECT.getData(original) == myProject) return original;

    return new DataContext() {
      public Object getData(String dataId) {
        if (PlatformDataKeys.PROJECT.is(dataId)) {
          return myProject;
        }
        return original.getData(dataId);
      }
    };
  }


  public EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e) {
    if (myGutterComponent != e.getSource()) return EditorMouseEventArea.EDITING_AREA;

    int x = myGutterComponent.convertX(e.getX());

    if (x >= myGutterComponent.getLineNumberAreaOffset() &&
        x < myGutterComponent.getLineNumberAreaOffset() + myGutterComponent.getLineNumberAreaWidth()) {
      return EditorMouseEventArea.LINE_NUMBERS_AREA;
    }

    if (x >= myGutterComponent.getAnnotationsAreaOffset() &&
        x <= myGutterComponent.getAnnotationsAreaOffset() + myGutterComponent.getAnnotationsAreaWidth()) {
      return EditorMouseEventArea.ANNOTATIONS_AREA;
    }

    if (x >= myGutterComponent.getLineMarkerAreaOffset() &&
        x < myGutterComponent.getLineMarkerAreaOffset() + myGutterComponent.getLineMarkerAreaWidth()) {
      return EditorMouseEventArea.LINE_MARKERS_AREA;
    }

    if (x >= myGutterComponent.getFoldingAreaOffset() &&
        x < myGutterComponent.getFoldingAreaOffset() + myGutterComponent.getFoldingAreaWidth()) {
      return EditorMouseEventArea.FOLDING_OUTLINE_AREA;
    }

    return null;
  }

  private void requestFocus() {
    myEditorComponent.requestFocus();
  }

  private void validateMousePointer(MouseEvent e) {
    if (e.getSource() == myGutterComponent) {
      FoldRegion foldingAtCursor = myGutterComponent.findFoldingAnchorAt(e.getX(), e.getY());
      myGutterComponent.setActiveFoldRegion(foldingAtCursor);
      if (foldingAtCursor != null) {
        myGutterComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        myGutterComponent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
    else {
      myGutterComponent.setActiveFoldRegion(null);
      if (getSelectionModel().hasSelection() && (e.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK)) == 0) {
        int offset = logicalPositionToOffset(xyToLogicalPosition(e.getPoint()));
        if (getSelectionModel().getSelectionStart() <= offset && offset < getSelectionModel().getSelectionEnd()) {
          myEditorComponent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          return;
        }
      }
      myEditorComponent.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
    }
  }

  private void runMouseDraggedCommand(final MouseEvent e) {
    if (myCommandProcessor == null || myMousePressedEvent != null && myMousePressedEvent.isConsumed()) {
      return;
    }
    myCommandProcessor.executeCommand(myProject, new Runnable() {
      public void run() {
        processMouseDragged(e);
      }
    }, "", MOUSE_DRAGGED_GROUP, UndoConfirmationPolicy.DEFAULT, getDocument());
  }

  private void processMouseDragged(MouseEvent e) {
    if (SwingUtilities.isRightMouseButton(e)) {
      return;
    }
    Rectangle rect = getScrollingModel().getVisibleArea();

    int x = e.getX();

    if (e.getSource() == myGutterComponent) {
      x = 0;
    }

    int dx = 0;
    if (x < rect.x && rect.x > 0) {
      dx = x - rect.x;
    }
    else {
      if (x > rect.x + rect.width) {
        dx = x - rect.x - rect.width;
      }
    }

    int dy = 0;
    int y = e.getY();
    if (y < rect.y && rect.y > 0) {
      dy = y - rect.y;
    }
    else {
      if (y > rect.y + rect.height) {
        dy = y - rect.y - rect.height;
      }
    }
    if (dx == 0 && dy == 0) {
      myScrollingTimer.stop();

      SelectionModel selectionModel = getSelectionModel();
      int oldSelectionStart = selectionModel.getLeadSelectionOffset();
      int oldCaretOffset = getCaretModel().getOffset();
      LogicalPosition oldLogicalCaret = getCaretModel().getLogicalPosition();
      moveCaretToScreenPos(x, y);
      getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

      int newCaretOffset = getCaretModel().getOffset();
      int caretShift = newCaretOffset - mySavedSelectionStart;

      if (myMousePressedEvent != null && getMouseEventArea(myMousePressedEvent) != EditorMouseEventArea.EDITING_AREA &&
          getMouseEventArea(myMousePressedEvent) != EditorMouseEventArea.LINE_NUMBERS_AREA) {
        selectionModel.setSelection(oldSelectionStart, newCaretOffset);
      }
      else {
        if (isColumnMode() || e.isAltDown()) {
          final LogicalPosition blockStart = selectionModel.hasBlockSelection() ? selectionModel.getBlockStart() : oldLogicalCaret;
          selectionModel.setBlockSelection(blockStart, getCaretModel().getLogicalPosition());
        }
        else {
          if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
            if (caretShift < 0) {
              int newSelection = newCaretOffset;
              if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                newSelection = mySelectionModel.getWordAtCaretStart();
              }
              else {
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                  newSelection =
                    logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line, 0)));
                }
              }
              if (newSelection < 0) newSelection = newCaretOffset;
              selectionModel.setSelection(mySavedSelectionEnd, newSelection);
              getCaretModel().moveToOffset(newSelection);
            }
            else {
              int newSelection = newCaretOffset;
              if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                newSelection = mySelectionModel.getWordAtCaretEnd();
              }
              else {
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                  newSelection =
                    logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0)));
                }
              }
              if (newSelection < 0) newSelection = newCaretOffset;
              selectionModel.setSelection(mySavedSelectionStart, newSelection);
              getCaretModel().moveToOffset(newSelection);
            }
            return;
          }

          if (!myMousePressedInsideSelection) {
            selectionModel.setSelection(oldSelectionStart, newCaretOffset);
          }
          else {
            if (caretShift != 0) {
              if (myMousePressedEvent != null) {
                if (mySettings.isDndEnabled()) {
                  boolean isCopy = UIUtil.isControlKeyDown(e) || isViewer() || !getDocument().isWritable();
                  mySavedCaretOffsetForDNDUndoHack = oldCaretOffset;
                  getContentComponent().getTransferHandler()
                    .exportAsDrag(getContentComponent(), e, isCopy ? TransferHandler.COPY : TransferHandler.MOVE);
                }
                else {
                  selectionModel.removeSelection();
                }
                myMousePressedEvent = null;
              }
            }
          }
        }
      }
    }
    else {
      myScrollingTimer.start(dx, dy);
    }
  }

  private static class RepaintCursorCommand implements Runnable {
    private long mySleepTime = 500;
    private boolean myIsBlinkCaret = true;
    private EditorImpl myEditor = null;
    private final MyRepaintRunnable myRepaintRunnable;
    private ScheduledFuture<?> mySchedulerHandle;

    private RepaintCursorCommand() {
      myRepaintRunnable = new MyRepaintRunnable();
    }

    private class MyRepaintRunnable implements Runnable {
      public void run() {
        if (myEditor != null) {
          myEditor.myCaretCursor.repaint();
        }
      }
    }

    public void start() {
      if (mySchedulerHandle != null) {
        mySchedulerHandle.cancel(false);
      }
      mySchedulerHandle = JobScheduler.getScheduler().scheduleAtFixedRate(this, mySleepTime, mySleepTime, TimeUnit.MILLISECONDS);
    }

    private void setBlinkPeriod(int blinkPeriod) {
      mySleepTime = blinkPeriod > 10 ? blinkPeriod : 10;
      start();
    }

    private void setBlinkCaret(boolean value) {
      myIsBlinkCaret = value;
    }

    public void run() {
      if (myEditor != null) {
        CaretCursor activeCursor = myEditor.myCaretCursor;

        long time = System.currentTimeMillis();
        time -= activeCursor.myStartTime;

        if (time > mySleepTime) {
          boolean toRepaint = true;
          if (myIsBlinkCaret) {
            activeCursor.isVisible = !activeCursor.isVisible;
          }
          else {
            toRepaint = !activeCursor.isVisible;
            activeCursor.isVisible = true;
          }

          if (toRepaint) {
            SwingUtilities.invokeLater(myRepaintRunnable);
          }
        }
      }
    }
  }

  void updateCaretCursor() {
    if (!ourIsUnitTestMode && !IJSwingUtilities.hasFocus(getContentComponent())) {
      stopOptimizedScrolling();
    }

    if (myCursorUpdater == null) {
      myCursorUpdater = new Runnable() {
        public void run() {
          if (myCursorUpdater == null) return;
          myCursorUpdater = null;
          VisualPosition caretPosition = getCaretModel().getVisualPosition();
          Point pos1 = visualPositionToXY(caretPosition);
          Point pos2 = visualPositionToXY(new VisualPosition(caretPosition.line, caretPosition.column + 1));
          myCaretCursor.setPosition(pos1, pos2.x - pos1.x);
        }
      };
    }
  }

  public boolean setCaretVisible(boolean b) {
    boolean old = myCaretCursor.isActive();
    if (b) {
      myCaretCursor.activate();
    }
    else {
      myCaretCursor.passivate();
    }
    return old;
  }

  public void addFocusListener(FocusChangeListener listener) {
    myFocusListeners.add(listener);
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isOneLineMode() {
    return myIsOneLineMode;
  }

  public boolean isEmbeddedIntoDialogWrapper() {
    return myEmbeddedIntoDialogWrapper;
  }

  public void setEmbeddedIntoDialogWrapper(boolean b) {
    assertIsDispatchThread();

    myEmbeddedIntoDialogWrapper = b;
    myScrollPane.setFocusable(!b);
    myEditorComponent.setFocusCycleRoot(!b);
    myEditorComponent.setFocusable(b);
  }

  public void setOneLineMode(boolean isOneLineMode) {
    myIsOneLineMode = isOneLineMode;
    getScrollPane().setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
    reinitSettings();
  }

  public void stopOptimizedScrolling() {
    myEditorComponent.setOpaque(false);
  }

  private void startOptimizedScrolling() {
    myEditorComponent.setOpaque(true);
  }

  private class CaretCursor {
    private Point myLocation;
    private int myWidth;

    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private boolean isVisible = false;
    private long myStartTime = 0;

    private CaretCursor() {
      myLocation = new Point(0, 0);
    }

    private void activate() {
      final boolean blink = mySettings.isBlinkCaret();
      final int blinkPeriod = mySettings.getCaretBlinkPeriod();
      synchronized (ourCaretBlinkingCommand) {
        ourCaretBlinkingCommand.myEditor = EditorImpl.this;
        ourCaretBlinkingCommand.setBlinkCaret(blink);
        ourCaretBlinkingCommand.setBlinkPeriod(blinkPeriod);
        isVisible = true;
      }
    }

    public boolean isActive() {
      synchronized (ourCaretBlinkingCommand) {
        return isVisible;
      }
    }

    private void passivate() {
      synchronized (ourCaretBlinkingCommand) {
        isVisible = false;
      }
    }

    private void setPosition(Point location, int width) {
      myStartTime = System.currentTimeMillis();
      myLocation = location;
      isVisible = true;
      myWidth = Math.max(width, 2);
      repaint();
    }

    private void repaint() {
      myEditorComponent.repaintEditorComponent(myLocation.x, myLocation.y, myWidth, getLineHeight());
    }

    private void paint(Graphics g) {
      if (!isVisible || !IJSwingUtilities.hasFocus(getContentComponent()) || isRendererMode()) return;

      int x = myLocation.x;
      int lineHeight = getLineHeight();
      int y = myLocation.y;

      Rectangle viewRect = getScrollingModel().getVisibleArea();
      if (x - viewRect.x < 0) {
        return;
      }


      g.setColor(myScheme.getColor(EditorColors.CARET_COLOR));

      if (myIsInsertMode != mySettings.isBlockCursor()) {
        for (int i = 0; i < mySettings.getLineCursorWidth(); i++) {
          UIUtil.drawLine(g, x + i, y, x + i, y + lineHeight - 1);
        }
      }
      else {
        Color background = myScheme.getColor(EditorColors.CARET_ROW_COLOR);
        if (background == null) background = getBackroundColor();
        g.setXORMode(background);

        g.fillRect(x, y, myWidth, lineHeight - 1);

        g.setPaintMode();
      }
    }
  }

  private class ScrollingTimer {
    Timer myTimer;
    private static final int TIMER_PERIOD = 100;
    private static final int CYCLE_SIZE = 20;
    private int myXCycles;
    private int myYCycles;
    private int myDx;
    private int myDy;
    private int xPassedCycles = 0;
    private int yPassedCycles = 0;

    private void start(int dx, int dy) {
      myDx = 0;
      myDy = 0;
      if (dx > 0) {
        myXCycles = CYCLE_SIZE / dx + 1;
        myDx = 1 + dx / CYCLE_SIZE;
      }
      else {
        if (dx < 0) {
          myXCycles = -CYCLE_SIZE / dx + 1;
          myDx = -1 + dx / CYCLE_SIZE;
        }
      }

      if (dy > 0) {
        myYCycles = CYCLE_SIZE / dy + 1;
        myDy = 1 + dy / CYCLE_SIZE;
      }
      else {
        if (dy < 0) {
          myYCycles = -CYCLE_SIZE / dy + 1;
          myDy = -1 + dy / CYCLE_SIZE;
        }
      }

      if (myTimer != null) {
        return;
      }


      myTimer = new Timer(TIMER_PERIOD, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myCommandProcessor.executeCommand(myProject, new DocumentRunnable(myDocument, myProject) {
            public void run() {
              int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();
              LogicalPosition caretPosition = getCaretModel().getLogicalPosition();
              int columnNumber = caretPosition.column;
              xPassedCycles++;
              if (xPassedCycles >= myXCycles) {
                xPassedCycles = 0;
                columnNumber += myDx;
              }

              int lineNumber = caretPosition.line;
              yPassedCycles++;
              if (yPassedCycles >= myYCycles) {
                yPassedCycles = 0;
                lineNumber += myDy;
              }

              LogicalPosition pos = new LogicalPosition(lineNumber, columnNumber);
              getCaretModel().moveToLogicalPosition(pos);
              getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

              int newCaretOffset = getCaretModel().getOffset();
              int caretShift = newCaretOffset - mySavedSelectionStart;

              if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
                if (caretShift < 0) {
                  int newSelection = newCaretOffset;
                  if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                    newSelection = mySelectionModel.getWordAtCaretStart();
                  }
                  else {
                    if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                      newSelection =
                        logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line, 0)));
                    }
                  }
                  if (newSelection < 0) newSelection = newCaretOffset;
                  mySelectionModel.setSelection(validateOffset(mySavedSelectionEnd), newSelection);
                  getCaretModel().moveToOffset(newSelection);
                }
                else {
                  int newSelection = newCaretOffset;
                  if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                    newSelection = mySelectionModel.getWordAtCaretEnd();
                  }
                  else {
                    if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                      newSelection = logicalPositionToOffset(
                        visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0)));
                    }
                  }
                  if (newSelection < 0) newSelection = newCaretOffset;
                  mySelectionModel.setSelection(validateOffset(mySavedSelectionStart), newSelection);
                  getCaretModel().moveToOffset(newSelection);
                }
                return;
              }

              if (mySelectionModel.hasBlockSelection()) {
                mySelectionModel.setBlockSelection(mySelectionModel.getBlockStart(), getCaretModel().getLogicalPosition());
              }
              else {
                mySelectionModel.setSelection(oldSelectionStart, getCaretModel().getOffset());
              }
            }
          }, EditorBundle.message("move.cursor.command.name"), DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT, getDocument());
        }
      });
      myTimer.start();
    }

    private void stop() {
      if (myTimer != null) {
        myTimer.stop();
        myTimer = null;
      }
    }

    private int validateOffset(int offset) {
      if (offset < 0) return 0;
      if (offset > myDocument.getTextLength()) return myDocument.getTextLength();
      return offset;
    }
  }

  class MyScrollBar extends JScrollBar {
    @NonNls private static final String DECR_BUTTON_FIELD = "decrButton";
    @NonNls private static final String INCR_BUTTON_FIELD = "incrButton";
    @NonNls private static final String APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS = "apple.laf.AquaScrollBarUI";

    private MyScrollBar(int orientation) {
      super(orientation);
      setFocusable(false);
      putClientProperty("JScrollBar.fastWheelScrolling", Boolean.TRUE); // fast scrolling for JDK 6
    }

    /**
     * This is helper method. It returns height of the top (descrease) scrollbar
     * button. Please note, that it's possible to return real height only if scrollbar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getDecScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      if (barUI instanceof BasicScrollBarUI) {
        try {
          Field decrButtonField = BasicScrollBarUI.class.getDeclaredField(DECR_BUTTON_FIELD);
          decrButtonField.setAccessible(true);
          JButton decrButtonValue = (JButton)decrButtonField.get(barUI);
          LOG.assertTrue(decrButtonValue != null);
          return insets.top + decrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc.getMessage());
        }
      }
      else {
        return insets.top + 15;
      }
    }

    /**
     * This is helper method. It returns height of the bottom (increase) scrollbar
     * button. Please note, that it's possible to return real height only if scrollbar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getIncScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      if (barUI instanceof BasicScrollBarUI) {
        try {
          Field incrButtonField = BasicScrollBarUI.class.getDeclaredField(INCR_BUTTON_FIELD);
          incrButtonField.setAccessible(true);
          JButton incrButtonValue = (JButton)incrButtonField.get(barUI);
          LOG.assertTrue(incrButtonValue != null);
          return insets.bottom + incrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc.getMessage());
        }
      }
      else if (APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS.equals(barUI.getClass().getName())) {
        return insets.bottom + 30;
      }
      else {
        return insets.bottom + 15;
      }
    }

    public int getUnitIncrement(int direction) {
      JViewport vp = myScrollPane.getViewport();
      Rectangle vr = vp.getViewRect();
      return myEditorComponent.getScrollableUnitIncrement(vr, SwingConstants.VERTICAL, direction);
    }

    public int getBlockIncrement(int direction) {
      JViewport vp = myScrollPane.getViewport();
      Rectangle vr = vp.getViewRect();
      return myEditorComponent.getScrollableBlockIncrement(vr, SwingConstants.VERTICAL, direction);
    }
  }

  private MyEditable getViewer() {
    if (myEditable == null) {
      myEditable = new MyEditable();
    }
    return myEditable;
  }

  public CopyProvider getCopyProvider() {
    return getViewer();
  }

  public CutProvider getCutProvider() {
    return getViewer();
  }

  public PasteProvider getPasteProvider() {

    return getViewer();
  }

  public DeleteProvider getDeleteProvider() {
    return getViewer();
  }

  private class MyEditable implements CutProvider, CopyProvider, PasteProvider, DeleteProvider {
    public void performCopy(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_COPY, dataContext);
    }

    public boolean isCopyEnabled(DataContext dataContext) {
      return true;
    }

    public boolean isCopyVisible(DataContext dataContext) {
      return getSelectionModel().hasSelection() || getSelectionModel().hasBlockSelection();
    }

    public void performCut(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_CUT, dataContext);
    }

    public boolean isCutEnabled(DataContext dataContext) {
      return !isViewer() && getDocument().isWritable();
    }

    public boolean isCutVisible(DataContext dataContext) {
      return getSelectionModel().hasSelection() || getSelectionModel().hasBlockSelection();
    }

    public void performPaste(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_PASTE, dataContext);
    }

    public boolean isPastePossible(DataContext dataContext) {
      // Copy of isPasteEnabled. See interface method javadoc.
      return !isViewer() && getDocument().isWritable();
    }

    public boolean isPasteEnabled(DataContext dataContext) {
      return !isViewer() && getDocument().isWritable();
    }

    public void deleteElement(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_DELETE, dataContext);
    }

    public boolean canDeleteElement(DataContext dataContext) {
      return !isViewer() && getDocument().isWritable();
    }

    private void executeAction(String actionId, DataContext dataContext) {
      EditorAction action = (EditorAction)ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        action.actionPerformed(EditorImpl.this, dataContext);
      }
    }
  }

  public void setColorsScheme(@NotNull EditorColorsScheme scheme) {
    assertIsDispatchThread();
    myScheme = scheme;
    reinitSettings();
  }

  @NotNull
  public EditorColorsScheme getColorsScheme() {
    assertReadAccess();
    return myScheme;
  }

  void assertIsDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditorComponent);
  }
  private static void assertReadAccess() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessAllowed();
  }

  public void setVerticalScrollbarOrientation(int type) {
    assertIsDispatchThread();
    int currentHorOffset = myScrollingModel.getHorizontalScrollOffset();
    myScrollbarOrientation = type;
    if (type == VERTICAL_SCROLLBAR_LEFT) {
      myScrollPane.setLayout(new LeftHandScrollbarLayout());
    }
    else {
      myScrollPane.setLayout(new ScrollPaneLayout());
    }
    myScrollingModel.scrollHorizontally(currentHorOffset);
  }

  public void setVerticalScrollbarVisible(boolean b) {
    if (b) {
      myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    }
    else {
      myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    }
  }

  public void setHorizontalScrollbarVisible(boolean b) {
    if (b) {
      myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }
    else {
      myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }
  }

  int getVerticalScrollbarOrientation() {
    return myScrollbarOrientation;
  }

  MyScrollBar getVerticalScrollBar() {
    return myVerticalScrollBar;
  }

  JPanel getPanel() {
    return myPanel;
  }

  private int getMouseSelectionState() {
    return myMouseSelectionState;
  }

  private void setMouseSelectionState(int mouseSelectionState) {
    myMouseSelectionState = mouseSelectionState;
    myMouseSelectionChangeTimestamp = System.currentTimeMillis();
  }


  void replaceInputMethodText(InputMethodEvent e) {
    getInputMethodRequests();
    myInputMethodRequestsHandler.replaceInputMethodText(e);
  }

  void inputMethodCaretPositionChanged(InputMethodEvent e) {
    getInputMethodRequests();
    myInputMethodRequestsHandler.setInputMethodCaretPosition(e);
  }

  InputMethodRequests getInputMethodRequests() {
    if (myInputMethodRequestsHandler == null) {
      myInputMethodRequestsHandler = new MyInputMethodHandler();
      myInputMethodRequestsSwingWrapper = new MyInputMethodHandleSwingThreadWrapper(myInputMethodRequestsHandler);
    }
    return myInputMethodRequestsSwingWrapper;
  }

  public boolean processKeyTyped(KeyEvent e) {
    if (e.getID() != KeyEvent.KEY_TYPED) return false;
    char c = e.getKeyChar();
    if (UIUtil.isReallyTypedEvent(e)) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
      processKeyTyped(c);
      return true;
    }
    else {
      return false;
    }
  }

  void beforeModalityStateChanged() {
    myScrollingModel.beforeModalityStateChanged();
  }

  public EditorDropHandler getDropHandler() {
    return myDropHandler;
  }

  public void setDropHandler(EditorDropHandler dropHandler) {
    myDropHandler = dropHandler;
  }

  private static class MyInputMethodHandleSwingThreadWrapper implements InputMethodRequests {
    private final InputMethodRequests myDelegate;

    private MyInputMethodHandleSwingThreadWrapper(InputMethodRequests delegate) {
      myDelegate = delegate;
    }

    public Rectangle getTextLocation(final TextHitInfo offset) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getTextLocation(offset);

      final Rectangle[] r = new Rectangle[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getTextLocation(offset);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public TextHitInfo getLocationOffset(final int x, final int y) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getLocationOffset(x, y);

      final TextHitInfo[] r = new TextHitInfo[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getLocationOffset(x, y);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public int getInsertPositionOffset() {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getInsertPositionOffset();

      final int[] r = new int[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getInsertPositionOffset();
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public AttributedCharacterIterator getCommittedText(final int beginIndex,
                                                        final int endIndex,
                                                        final AttributedCharacterIterator.Attribute[] attributes) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        return myDelegate.getCommittedText(beginIndex, endIndex, attributes);
      }
      final AttributedCharacterIterator[] r = new AttributedCharacterIterator[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getCommittedText(beginIndex, endIndex, attributes);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public int getCommittedTextLength() {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getCommittedTextLength();
      final int[] r = new int[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getCommittedTextLength();
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    public AttributedCharacterIterator getSelectedText(final AttributedCharacterIterator.Attribute[] attributes) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getSelectedText(attributes);

      final AttributedCharacterIterator[] r = new AttributedCharacterIterator[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getSelectedText(attributes);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }
  }

  private class MyInputMethodHandler implements InputMethodRequests {
    private String composedText;
    private int composedTextStart;
    private int composedTextEnd;

    public Rectangle getTextLocation(TextHitInfo offset) {
      Point caret = logicalPositionToXY(getCaretModel().getLogicalPosition());
      Rectangle r = new Rectangle(caret, new Dimension(1, getLineHeight()));
      Point p = getContentComponent().getLocationOnScreen();
      r.translate(p.x, p.y);

      return r;
    }

    public TextHitInfo getLocationOffset(int x, int y) {
      if (composedText != null) {
        Point p = getContentComponent().getLocationOnScreen();
        p.x = x - p.x;
        p.y = y - p.y;
        int pos = logicalPositionToOffset(xyToLogicalPosition(p));
        if (pos >= composedTextStart && pos <= composedTextEnd) {
          return TextHitInfo.leading(pos - composedTextStart);
        }
      }
      return null;
    }

    public int getInsertPositionOffset() {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedText != null) {
        composedStartIndex = composedTextStart;
        composedEndIndex = composedTextEnd;
      }

      int caretIndex = getCaretModel().getOffset();

      if (caretIndex < composedStartIndex) {
        return caretIndex;
      }
      else {
        if (caretIndex < composedEndIndex) {
          return composedStartIndex;
        }
        else {
          return caretIndex - (composedEndIndex - composedStartIndex);
        }
      }
    }

    private String getText(int startIdx, int endIdx) {
      CharSequence chars = getDocument().getCharsSequence();
      return chars.subSequence(startIdx, endIdx).toString();
    }

    public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, AttributedCharacterIterator.Attribute[] attributes) {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedText != null) {
        composedStartIndex = composedTextStart;
        composedEndIndex = composedTextEnd;
      }

      String committed;
      if (beginIndex < composedStartIndex) {
        if (endIndex <= composedStartIndex) {
          committed = getText(beginIndex, endIndex - beginIndex);
        }
        else {
          int firstPartLength = composedStartIndex - beginIndex;
          committed = getText(beginIndex, firstPartLength) + getText(composedEndIndex, endIndex - beginIndex - firstPartLength);
        }
      }
      else {
        committed = getText(beginIndex + (composedEndIndex - composedStartIndex), endIndex - beginIndex);
      }

      return new AttributedString(committed).getIterator();
    }

    public int getCommittedTextLength() {
      int length = getDocument().getTextLength();
      if (composedText != null) {
        length -= composedText.length();
      }
      return length;
    }

    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
      String text = getSelectionModel().getSelectedText();
      return text == null ? null : new AttributedString(text).getIterator();
    }

    private void createComposedString(int composedIndex, AttributedCharacterIterator text) {
      StringBuffer strBuf = new StringBuffer();

      // create attributed string with no attributes
      for (char c = text.setIndex(composedIndex); c != CharacterIterator.DONE; c = text.next()) {
        strBuf.append(c);
      }

      composedText = new String(strBuf);
    }

    private void setInputMethodCaretPosition(InputMethodEvent e) {
      if (composedText != null) {
        int dot = composedTextStart;

        TextHitInfo caretPos = e.getCaret();
        if (caretPos != null) {
          dot += caretPos.getInsertionIndex();
        }

        getCaretModel().moveToOffset(dot);
        getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }

    private void runUndoTransparent(final Runnable runnable) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(runnable);
            }
          }, "", getDocument(), UndoConfirmationPolicy.DEFAULT, getDocument());
        }
      });
    }

    private void replaceInputMethodText(InputMethodEvent e) {
      int commitCount = e.getCommittedCharacterCount();
      AttributedCharacterIterator text = e.getText();

      // old composed text deletion
      final Document doc = getDocument();

      if (composedText != null) {
        if (!isViewer() && doc.isWritable()) {
          runUndoTransparent(new Runnable() {
            public void run() {
              doc.deleteString(Math.max(0, composedTextStart), Math.min(composedTextEnd, doc.getTextLength()));
            }
          });
        }
        composedText = null;
      }

      if (text != null) {
        text.first();

        // committed text insertion
        if (commitCount > 0) {
          //noinspection ForLoopThatDoesntUseLoopVariable
          for (char c = text.current(); commitCount > 0; c = text.next(), commitCount--) {
            if (c >= 0x20 && c != 0x7F) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
              processKeyTyped(c);
            }
          }
        }

        // new composed text insertion
        if (!isViewer() && doc.isWritable()) {
          int composedTextIndex = text.getIndex();
          if (composedTextIndex < text.getEndIndex()) {
            createComposedString(composedTextIndex, text);

            runUndoTransparent(new Runnable() {
              public void run() {
                EditorModificationUtil.insertStringAtCaret(EditorImpl.this, composedText, false, false);
              }
            });

            composedTextStart = getCaretModel().getOffset();
            composedTextEnd = getCaretModel().getOffset() + composedText.length();
          }
        }
      }
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    public void mousePressed(MouseEvent e) {
      requestFocus();
      runMousePressedCommand(e);
    }

    public void mouseReleased(MouseEvent e) {
      runMouseReleasedCommand(e);
      if (!e.isConsumed() && myMousePressedEvent != null && !myMousePressedEvent.isConsumed() &&
          Math.abs(e.getX() - myMousePressedEvent.getX()) < EditorUtil.getSpaceWidth(Font.PLAIN, EditorImpl.this) &&
          Math.abs(e.getY() - myMousePressedEvent.getY()) < getLineHeight()) {
        runMouseClickedCommand(e);
      }
      myMousePressedEvent = null;
    }

    public void mouseEntered(MouseEvent e) {
      runMouseEnteredCommand(e);
    }

    public void mouseExited(MouseEvent e) {
      runMouseExitedCommand(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseExited(e);
      }

      TooltipController.getInstance().cancelTooltip(FOLDING_TOOLTIP_GROUP);
    }
    private void runMousePressedCommand(final MouseEvent e) {
      myMousePressedEvent = e;
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));

      for (EditorMouseListener mouseListener : myMouseListeners) {
        mouseListener.mousePressed(event);
      }

      // On some systems (for example on Linux) popup trigger is MOUSE_PRESSED event.
      // But this trigger is always consumed by popup handler. In that case we have to
      // also move caret.
      if (event.isConsumed() && !(event.getMouseEvent().isPopupTrigger() || event.getArea() == EditorMouseEventArea.EDITING_AREA)) {
        return;
      }

      if (myCommandProcessor != null) {
        Runnable runnable = new Runnable() {
          public void run() {
            processMousePressed(e);
          }
        };
        myCommandProcessor.executeCommand(myProject, runnable, "", DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT, getDocument());
      }
      else {
        processMousePressed(e);
      }
    }

    private void runMouseClickedCommand(final MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseClicked(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private void runMouseReleasedCommand(final MouseEvent e) {
      myScrollingTimer.stop();
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseReleased(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }

      if (myCommandProcessor != null) {
        Runnable runnable = new Runnable() {
          public void run() {
            processMouseReleased(e);
          }
        };
        myCommandProcessor.executeCommand(myProject, runnable, "", DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT, getDocument());
      }
      else {
        processMouseReleased(e);
      }
    }

    private void runMouseEnteredCommand(MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseEntered(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private void runMouseExitedCommand(MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseExited(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private void processMousePressed(MouseEvent e) {
      myInitialMouseEvent = e;

      if (myMouseSelectionState != MOUSE_SELECTION_STATE_NONE && System.currentTimeMillis() - myMouseSelectionChangeTimestamp > 1000) {
        setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);
      }

      int x = e.getX();
      int y = e.getY();

      if (x < 0) x = 0;
      if (y < 0) y = 0;

      final EditorMouseEventArea eventArea = getMouseEventArea(e);
      if (eventArea == EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
        final FoldRegion range = myGutterComponent.findFoldingAnchorAt(x, y);
        if (range != null) {
          final boolean expansion = !range.isExpanded();

          int scrollShift = y - getScrollingModel().getVerticalScrollOffset();
          Runnable processor = new Runnable() {
            public void run() {
              myFoldingModel.flushCaretShift();
              range.setExpanded(expansion);
            }
          };
          getFoldingModel().runBatchFoldingOperation(processor);
          y = myGutterComponent.getHeadCenterY(range);
          getScrollingModel().scrollVertically(y - scrollShift);
          return;
        }
      }

      if (e.getSource() == myGutterComponent) {
        if (eventArea == EditorMouseEventArea.LINE_MARKERS_AREA || eventArea == EditorMouseEventArea.ANNOTATIONS_AREA || eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA) {
          myGutterComponent.mousePressed(e);
          if (e.isConsumed()) return;
        }
        x = 0;
      }

      int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();
      moveCaretToScreenPos(x, y);

      if (e.isPopupTrigger()) return;

      requestFocus();

      int caretOffset = getCaretModel().getOffset();

      myMouseSelectedRegion = myFoldingModel.getFoldingPlaceholderAt(new Point(x, y));
      myMousePressedInsideSelection = mySelectionModel.hasSelection() && caretOffset >= mySelectionModel.getSelectionStart() &&
                                      caretOffset <= mySelectionModel.getSelectionEnd();

      if (!myMousePressedInsideSelection && mySelectionModel.hasBlockSelection()) {
        int[] starts = mySelectionModel.getBlockSelectionStarts();
        int[] ends = mySelectionModel.getBlockSelectionEnds();
        for (int i = 0; i < starts.length; i++) {
          if (caretOffset >= starts[i] && caretOffset < ends[i]) {
            myMousePressedInsideSelection = true;
            break;
          }
        }
      }

      if (getMouseEventArea(e) == EditorMouseEventArea.LINE_NUMBERS_AREA && e.getClickCount() == 1) {
        mySelectionModel.selectLineAtCaret();
        setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
        mySavedSelectionStart = mySelectionModel.getSelectionStart();
        mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
        return;
      }

      if (e.isShiftDown() && !e.isControlDown() && !e.isAltDown()) {
        if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
          if (caretOffset < mySavedSelectionStart) {
            mySelectionModel.setSelection(mySavedSelectionEnd, caretOffset);
          }
          else {
            mySelectionModel.setSelection(mySavedSelectionStart, caretOffset);
          }
        }
        else {
          mySelectionModel.setSelection(oldSelectionStart, caretOffset);
        }
      }
      else {
        if (!myMousePressedInsideSelection && (getSelectionModel().hasSelection() || getSelectionModel().hasBlockSelection())) {
          setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);
          mySelectionModel.setSelection(caretOffset, caretOffset);
        }
        else {
          if (!e.isPopupTrigger()) {
            switch (e.getClickCount()) {
              case 2:
                mySelectionModel.selectWordAtCaret(mySettings.isMouseClickSelectionHonorsCamelWords());
                setMouseSelectionState(MOUSE_SELECTION_STATE_WORD_SELECTED);
                mySavedSelectionStart = mySelectionModel.getSelectionStart();
                mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
                getCaretModel().moveToOffset(mySavedSelectionEnd);
                break;

              case 3:
                mySelectionModel.selectLineAtCaret();
                setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
                mySavedSelectionStart = mySelectionModel.getSelectionStart();
                mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
                break;
            }
          }
        }
      }
    }
  }

  private static final TooltipGroup FOLDING_TOOLTIP_GROUP = new TooltipGroup("FOLDING_TOOLTIP_GROUP", 10);

  private class MyMouseMotionListener implements MouseMotionListener {
    public void mouseDragged(MouseEvent e) {
      validateMousePointer(e);
      runMouseDraggedCommand(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseDragged(e);
      }

      for (EditorMouseMotionListener listener : myMouseMotionListeners) {
        listener.mouseDragged(event);
      }
    }

    public void mouseMoved(MouseEvent e) {
      validateMousePointer(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (e.getSource() == myGutterComponent) {
        myGutterComponent.mouseMoved(e);
      }

      if (event.getArea() == EditorMouseEventArea.EDITING_AREA) {
        FoldRegion fold = myFoldingModel.getFoldingPlaceholderAt(e.getPoint());
        TooltipController controller = TooltipController.getInstance();
        if (fold != null) {
          DocumentFragment range = createDocumentFragment(fold);
          final Point p =
            SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), getComponent().getRootPane().getLayeredPane());
          controller.showTooltip(EditorImpl.this, p, new DocumentFragmentTooltipRenderer(range), false, FOLDING_TOOLTIP_GROUP);
        }
        else {
          controller.cancelTooltip(FOLDING_TOOLTIP_GROUP);
        }
      }

      for (EditorMouseMotionListener listener : myMouseMotionListeners) {
        listener.mouseMoved(event);
      }
    }

    private DocumentFragment createDocumentFragment(FoldRegion fold) {
      final FoldingGroup group = fold.getGroup();
      final int foldStart = fold.getStartOffset();
      if (group != null) {
        final int endOffset = myFoldingModel.getEndOffset(group);
        if (offsetToVisualPosition(endOffset).line == offsetToVisualPosition(foldStart).line) {
          return new DocumentFragment(myDocument, foldStart, endOffset);
        }
      }

      final int oldEnd = fold.getEndOffset();
      return new DocumentFragment(myDocument, foldStart, oldEnd);
    }
  }

  private class MyColorSchemeDelegate implements EditorColorsScheme {
    private final HashMap<TextAttributesKey, TextAttributes> myOwnAttributes = new HashMap<TextAttributesKey, TextAttributes>();
    private final HashMap<ColorKey, Color> myOwnColors = new HashMap<ColorKey, Color>();
    private Map<EditorFontType, Font> myFontsMap = null;
    private int myFontSize = -1;
    private String myFaceName = null;
    private EditorColorsScheme myGlobalScheme;

    private MyColorSchemeDelegate() {
      updateGlobalScheme();
    }

    private EditorColorsScheme getGlobal() {
      return myGlobalScheme;
    }

    public String getName() {
      return getGlobal().getName();
    }


    protected void initFonts() {
      String editorFontName = getEditorFontName();
      int editorFontSize = getEditorFontSize();

      myFontsMap = new EnumMap<EditorFontType, Font>(EditorFontType.class);

      Font plainFont = new Font(editorFontName, Font.PLAIN, editorFontSize);
      Font boldFont = new Font(editorFontName, Font.BOLD, editorFontSize);
      Font italicFont = new Font(editorFontName, Font.ITALIC, editorFontSize);
      Font boldItalicFont = new Font(editorFontName, Font.BOLD + Font.ITALIC, editorFontSize);

      myFontsMap.put(EditorFontType.PLAIN, plainFont);
      myFontsMap.put(EditorFontType.BOLD, boldFont);
      myFontsMap.put(EditorFontType.ITALIC, italicFont);
      myFontsMap.put(EditorFontType.BOLD_ITALIC, boldItalicFont);

      reinitSettings();
    }

    public void setName(String name) {
      getGlobal().setName(name);
    }

    public TextAttributes getAttributes(TextAttributesKey key) {
      if (myOwnAttributes.containsKey(key)) return myOwnAttributes.get(key);
      return getGlobal().getAttributes(key);
    }

    public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
      myOwnAttributes.put(key, attributes);
    }

    public Color getDefaultBackground() {
      return getGlobal().getDefaultBackground();
    }

    public Color getDefaultForeground() {
      return getGlobal().getDefaultForeground();
    }

    public Color getColor(ColorKey key) {
      if (myOwnColors.containsKey(key)) return myOwnColors.get(key);
      return getGlobal().getColor(key);
    }

    public void setColor(ColorKey key, Color color) {
      myOwnColors.put(key, color);

      // These two are here because those attributes are cached and I do not whant the clients to call editor's reinit
      // settings in this case.
      myCaretModel.reinitSettings();
      mySelectionModel.reinitSettings();
    }

    public int getEditorFontSize() {
      if (myFontSize == -1) {
        return getGlobal().getEditorFontSize();
      }
      return myFontSize;
    }

    public void setEditorFontSize(int fontSize) {
      if (fontSize < 8) fontSize = 8;
      if (fontSize > 20) fontSize = 20;
      myFontSize = fontSize;
      initFonts();
    }

    public String getEditorFontName() {
      if (myFaceName == null) {
        return getGlobal().getEditorFontName();
      }
      return myFaceName;
    }

    public void setEditorFontName(String fontName) {
      myFaceName = fontName;
      initFonts();
    }

    public Font getFont(EditorFontType key) {
      if (myFontsMap != null) {
        Font font = myFontsMap.get(key);
        if (font != null) return font;
      }
      return getGlobal().getFont(key);
    }

    public void setFont(EditorFontType key, Font font) {
      if (myFontsMap == null) {
        initFonts();
      }
      myFontsMap.put(key, font);
      reinitSettings();
    }

    public float getLineSpacing() {
      return getGlobal().getLineSpacing();
    }

    public void setLineSpacing(float lineSpacing) {
      getGlobal().setLineSpacing(lineSpacing);
    }

    public Object clone() {
      return null;
    }

    public void readExternal(Element element) throws InvalidDataException {
    }

    public void writeExternal(Element element) throws WriteExternalException {
    }

    public void updateGlobalScheme() {
      myGlobalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    }
  }

  private static class MyTransferHandler extends TransferHandler {
    private RangeMarker myDraggedRange = null;

    private static Editor getEditor(JComponent comp) {
      EditorComponentImpl editorComponent = (EditorComponentImpl)comp;
      return editorComponent.getEditor();
    }

    public boolean importData(final JComponent comp, final Transferable t) {
      final EditorImpl editor = (EditorImpl)getEditor(comp);

      final EditorDropHandler dropHandler = editor.getDropHandler();
      if (dropHandler != null && dropHandler.canHandleDrop(t.getTransferDataFlavors())) {
        dropHandler.handleDrop(t, editor.getProject());
        return true;
      }

      final int caretOffset = editor.getCaretModel().getOffset();
      if (myDraggedRange != null && myDraggedRange.getStartOffset() <= caretOffset && caretOffset < myDraggedRange.getEndOffset()) {
        return false;
      }

      if (myDraggedRange != null) {
        editor.getCaretModel().moveToOffset(editor.mySavedCaretOffsetForDNDUndoHack);
      }

      CommandProcessor.getInstance().executeCommand(editor.myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                editor.getSelectionModel().removeSelection();
                final int offset;
                if (myDraggedRange != null) {
                  editor.getCaretModel().moveToOffset(caretOffset);
                  offset = caretOffset;
                }
                else {
                  offset = editor.getCaretModel().getOffset();
                }
                if (editor.getDocument().getRangeGuard(offset, offset) != null) return;

                EditorActionHandler pasteHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

                Transferable backup = null;
                try {
                  backup = clipboard.getContents(this);
                  clipboard.setContents(t, EmptyClipboardOwner.INSTANCE);
                }
                catch (Exception e) {
                  LOG.info("Error communicating with system clipboard", e);
                }

                editor.putUserData(LAST_PASTED_REGION, null);
                pasteHandler.execute(editor, editor.getDataContext());
                try {
                  if (backup != null) {
                    clipboard.setContents(backup, EmptyClipboardOwner.INSTANCE);
                  }
                }
                catch (IllegalStateException e) {
                  LOG.info(e);
                }

                TextRange range = editor.getUserData(LAST_PASTED_REGION);
                if (range != null) {
                  editor.getCaretModel().moveToOffset(range.getStartOffset());
                  editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
                }
              }
              catch (Exception exception) {
                LOG.error(exception);
              }
            }
          });
        }
      }, EditorBundle.message("paste.command.name"), DND_COMMAND_KEY, UndoConfirmationPolicy.DEFAULT, editor.getDocument());

      return true;
    }

    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
      Editor editor = getEditor(comp);
      final EditorDropHandler dropHandler = ((EditorImpl)editor).getDropHandler();
      if (dropHandler != null && dropHandler.canHandleDrop(transferFlavors)) {
        return true;
      }
      if (editor.isViewer()) return false;

      int offset = editor.getCaretModel().getOffset();
      if (editor.getDocument().getRangeGuard(offset, offset) != null) return false;

      for (DataFlavor transferFlavor : transferFlavors) {
        if (transferFlavor.equals(DataFlavor.stringFlavor)) return true;
      }

      return false;
    }

    protected Transferable createTransferable(JComponent c) {
      Editor editor = getEditor(c);
      String s = editor.getSelectionModel().getSelectedText();
      if (s == null) return null;
      int selectionStart = editor.getSelectionModel().getSelectionStart();
      int selectionEnd = editor.getSelectionModel().getSelectionEnd();
      myDraggedRange = editor.getDocument().createRangeMarker(selectionStart, selectionEnd);

      return new StringSelection(s);
    }

    public int getSourceActions(JComponent c) {
      return COPY_OR_MOVE;
    }

    protected void exportDone(final JComponent source, Transferable data, int action) {
      if (data == null) return;

      final Component last = DnDManager.getInstance().getLastDropHandler();

      if (last != null && !(last instanceof EditorComponentImpl)) return;

      final Editor editor = getEditor(source);
      if (action == MOVE && !editor.isViewer()) {
        if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), editor.getProject())) {
            return;
        }
        CommandProcessor.getInstance().executeCommand(((EditorImpl)editor).myProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                Document doc = editor.getDocument();
                doc.startGuardedBlockChecking();
                try {
                  doc.deleteString(myDraggedRange.getStartOffset(), myDraggedRange.getEndOffset());
                }
                catch (ReadOnlyFragmentModificationException e) {
                  EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
                }
                finally {
                  doc.stopGuardedBlockChecking();
                }
              }
            });
          }
        }, EditorBundle.message("move.selection.command.name"), DND_COMMAND_KEY, UndoConfirmationPolicy.DEFAULT, editor.getDocument());
      }

      myDraggedRange = null;
    }
  }

  class EditorDocumentAdapter implements PrioritizedDocumentListener {
    public void beforeDocumentChange(DocumentEvent e) {
      beforeChangedUpdate(e);
    }

    public void documentChanged(DocumentEvent e) {
      changedUpdate(e);
    }

    public int getPriority() {
      return 5;
    }
  }

  private class EditorSizeContainer {
    private TIntArrayList myLineWidths;
    private volatile boolean myIsDirty;
    private int myOldEndLine;
    private Dimension mySize;
    private int myMaxWidth = -1;

    public synchronized void reset() {
      int visLinesCount = getVisibleLineCount();
      myLineWidths = new TIntArrayList(visLinesCount + 300);
      int[] values = new int[visLinesCount];
      Arrays.fill(values, -1);
      myLineWidths.add(values);
      myIsDirty = true;
    }

    public synchronized void beforeChange(DocumentEvent e) {
      if (myDocument.isInBulkUpdate()) {
        myMaxWidth = mySize != null ? mySize.width : -1;
      }

      myOldEndLine = getVisualPositionLine(e.getOffset() + e.getOldLength());
    }

    private int getVisualPositionLine(int offset) {
      // Do round up of offset to the nearest line start (valid since we need only line)
      // This is needed for preventing access to lexer editor highlighter regions [that are reset] during bulk mode operation
      final int startLineOffset = myDocument.getLineStartOffset(calcLogicalLineNumber(offset));
      return offsetToVisualPosition(startLineOffset).line;
    }

    public synchronized void changedUpdate(DocumentEvent e) {
      int startLine = e.getOldLength() == 0 ? myOldEndLine : getVisualPositionLine(e.getOffset());
      int newEndLine = e.getNewLength() == 0 ? startLine : getVisualPositionLine(e.getOffset() + e.getNewLength());
      int oldEndLine = myOldEndLine;

      final int lineWidthSize = myLineWidths.size();
      if (lineWidthSize == 0) {
        reset();
      }
      else {
        final int min = Math.min(oldEndLine, newEndLine);
        final boolean toAddNewLines = min >= lineWidthSize;

        if (toAddNewLines) {
          final int[] delta = new int[min - lineWidthSize + 1];
          myLineWidths.insert(lineWidthSize, delta);
        }

        for (int i = startLine; i <= min; i++) myLineWidths.set(i, -1);
        if (newEndLine > oldEndLine) {
          int[] delta = new int[newEndLine - oldEndLine];
          Arrays.fill(delta, -1);
          myLineWidths.insert(oldEndLine + 1, delta);
        }
        else if (oldEndLine > newEndLine && !toAddNewLines && newEndLine + 1 < lineWidthSize) {
          myLineWidths.remove(newEndLine + 1, Math.min(oldEndLine, lineWidthSize) - newEndLine);
        }
        myIsDirty = true;
      }
    }

    private void validateSizes() {
      if (!myIsDirty) return;

      synchronized (this) {
        if (!myIsDirty) return;
        int lineCount = myLineWidths.size();

        if (myMaxWidth != -1 && myDocument.isInBulkUpdate()) {
          mySize = new Dimension(myMaxWidth, getLineHeight() * lineCount);
          myIsDirty = false;
          return;
        }

        final CharSequence text = myDocument.getCharsNoThreadCheck();
        int end = myDocument.getTextLength();
        int x = 0;
        final int fontSize = myScheme.getEditorFontSize();
        final String fontName = myScheme.getEditorFontName();

        for (int line = 0; line < lineCount; line++) {
          if (myLineWidths.getQuick(line) != -1) continue;
          x = 0;
          int offset = logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(line, 0)));

          if (offset >= myDocument.getTextLength()) {
            myLineWidths.set(line, 0);
            break;
          }

          IterationState state = new IterationState(EditorImpl.this, offset, false);
          int fontType = state.getMergedAttributes().getFontType();

          while (offset < end && line < lineCount) {
            char c = text.charAt(offset);
            if (offset >= state.getEndOffset()) {
              state.advance();
              fontType = state.getMergedAttributes().getFontType();
            }

            FoldRegion collapsed = state.getCurrentFold();
            if (collapsed != null) {
              String placeholder = collapsed.getPlaceholderText();
              for (int i = 0; i < placeholder.length(); i++) {
                x += EditorUtil.charWidth(placeholder.charAt(i), fontType, EditorImpl.this);
              }
              offset = collapsed.getEndOffset();
            }
            else {
              if (c == '\t') {
                x = EditorUtil.nextTabStop(x, EditorImpl.this);
                offset++;
              }
              else {
                if (c == '\n') {
                  myLineWidths.set(line, x);
                  if (line + 1 >= lineCount || myLineWidths.getQuick(line + 1) != -1) break;
                  offset++;
                  x = 0;
                  //noinspection AssignmentToForLoopParameter
                  line++;
                }
                else {
                  x += ComplementaryFontsRegistry.getFontAbleToDisplay(c, fontSize, fontType, fontName).charWidth(c, myEditorComponent);
                  offset++;
                }
              }
            }
          }
        }

        if (lineCount > 0) {
          myLineWidths.set(lineCount - 1,
                           x);    // Last line can be non-zero length and won't be caught by in-loop procedure since latter only react on \n's
        }

        int maxWidth = 0;
        for (int i = 0; i < lineCount; i++) {
          maxWidth = Math.max(maxWidth, myLineWidths.getQuick(i));
        }

        mySize = new Dimension(maxWidth, getLineHeight() * lineCount);

        myIsDirty = false;
      }
    }

    public Dimension getContentSize() {
      validateSizes();
      return mySize;
    }
  }

  @NotNull
  public EditorGutter getGutter() {
    return getGutterComponentEx();
  }

  public int calcColumnNumber(CharSequence text, int start, int offset, int tabSize) {
    IterationState state = new IterationState(this, start, false);
    int fontType = state.getMergedAttributes().getFontType();
    int column = 0;
    int x = 0;
    int spaceSize = EditorUtil.getSpaceWidth(fontType, this);
    for (int i = start; i < offset; i++) {
      if (i >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }

      char c = text.charAt(i);
      if (c == '\t') {
        int prevX = x;
        x = EditorUtil.nextTabStop(x, this);
        column += (x - prevX) / spaceSize;
        //column += Math.max(1, (x - prevX) / spaceSize);
      }
      else {
        x += EditorUtil.charWidth(c, fontType, this);
        column++;
      }
    }

    return column;
  }

  public void putInfo(Map<String, String> info) {
    final VisualPosition visual = getCaretModel().getVisualPosition();
    info.put("caret", visual.getLine() + ":" + visual.getColumn());
  }

  private class MyScrollPane extends JScrollPane2 {
    protected void processMouseWheelEvent(MouseWheelEvent e) {
      if (mySettings.isWheelFontChangeEnabled()) {
        boolean changeFontSize = SystemInfo.isMac
                                 ? !e.isControlDown() && e.isMetaDown() && !e.isAltDown() && !e.isShiftDown()
                                 : e.isControlDown() && !e.isMetaDown() && !e.isAltDown() && !e.isShiftDown();
        if (changeFontSize) {
          setFontSize(myScheme.getEditorFontSize() + e.getWheelRotation());
          return;
        }
      }

      super.processMouseWheelEvent(e);
    }
  }

  private class MyHeaderPanel extends JPanel {
    private int myOldHeight = 0;

    private MyHeaderPanel() {
      super(new BorderLayout());
    }

    public void revalidate() {
      myOldHeight = getHeight();
      super.revalidate();
    }

    protected void validateTree() {
      int height = myOldHeight;
      super.validateTree();
      height -= getHeight();

      if (height != 0) {
        myVerticalScrollBar.setValue(myVerticalScrollBar.getValue() - height);
      }
      myOldHeight = getHeight();
    }
  }
}

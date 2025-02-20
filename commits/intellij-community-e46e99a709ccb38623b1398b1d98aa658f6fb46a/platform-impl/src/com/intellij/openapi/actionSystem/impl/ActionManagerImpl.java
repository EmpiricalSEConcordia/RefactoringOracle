package com.intellij.openapi.actionSystem.impl;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Constructor;
import java.util.*;

public final class ActionManagerImpl extends ActionManagerEx implements JDOMExternalizable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionManagerImpl");
  private static final int TIMER_DELAY = 500;
  private static final int UPDATE_DELAY_AFTER_TYPING = 500;

  private final Object myLock = new Object();
  private final THashMap<String,Object> myId2Action;
  private final THashMap<PluginId, THashSet<String>> myPlugin2Id;
  private final TObjectIntHashMap<String> myId2Index;
  private final THashMap<Object,String> myAction2Id;
  private final ArrayList<String> myNotRegisteredInternalActionIds;
  private MyTimer myTimer;

  private int myRegisteredActionsCount;
  private final ArrayList<AnActionListener> myActionListeners;
  private AnActionListener[] myCachedActionListeners;
  private String myLastPreformedActionId;
  private final KeymapManager myKeymapManager;
  private final DataManager myDataManager;
  private String myPrevPerformedActionId;
  private long myLastTimeEditorWasTypedIn = 0;
  @NonNls public static final String ACTION_ELEMENT_NAME = "action";
  @NonNls public static final String GROUP_ELEMENT_NAME = "group";
  @NonNls public static final String ACTIONS_ELEMENT_NAME = "actions";
  @NonNls public static final String CLASS_ATTR_NAME = "class";
  @NonNls public static final String ID_ATTR_NAME = "id";
  @NonNls public static final String INTERNAL_ATTR_NAME = "internal";
  @NonNls public static final String ICON_ATTR_NAME = "icon";
  @NonNls public static final String ADD_TO_GROUP_ELEMENT_NAME = "add-to-group";
  @NonNls public static final String SHORTCUT_ELEMENT_NAME = "keyboard-shortcut";
  @NonNls public static final String MOUSE_SHORTCUT_ELEMENT_NAME = "mouse-shortcut";
  @NonNls public static final String DESCRIPTION = "description";
  @NonNls public static final String TEXT_ATTR_NAME = "text";
  @NonNls public static final String POPUP_ATTR_NAME = "popup";
  @NonNls public static final String SEPARATOR_ELEMENT_NAME = "separator";
  @NonNls public static final String REFERENCE_ELEMENT_NAME = "reference";
  @NonNls public static final String GROUPID_ATTR_NAME = "group-id";
  @NonNls public static final String ANCHOR_ELEMENT_NAME = "anchor";
  @NonNls public static final String FIRST = "first";
  @NonNls public static final String LAST = "last";
  @NonNls public static final String BEFORE = "before";
  @NonNls public static final String AFTER = "after";
  @NonNls public static final String RELATIVE_TO_ACTION_ATTR_NAME = "relative-to-action";
  @NonNls public static final String FIRST_KEYSTROKE_ATTR_NAME = "first-keystroke";
  @NonNls public static final String SECOND_KEYSTROKE_ATTR_NAME = "second-keystroke";
  @NonNls public static final String KEYMAP_ATTR_NAME = "keymap";
  @NonNls public static final String KEYSTROKE_ATTR_NAME = "keystroke";
  @NonNls public static final String REF_ATTR_NAME = "ref";
  @NonNls public static final String ACTIONS_BUNDLE = "messages.ActionsBundle";
  @NonNls public static final String USE_SHORTCUT_OF_ATTR_NAME = "use-shortcut-of";

  private List<ActionPopupMenuImpl> myPopups = new ArrayList<ActionPopupMenuImpl>();
  private Map<AnAction, DataContext> myQueuedNotifications = new LinkedHashMap<AnAction, DataContext>();
  private Runnable myPreloadActionsRunnable;

  ActionManagerImpl(KeymapManager keymapManager, DataManager dataManager) {
    myId2Action = new THashMap<String, Object>();
    myId2Index = new TObjectIntHashMap<String>();
    myAction2Id = new THashMap<Object, String>();
    myPlugin2Id = new THashMap<PluginId, THashSet<String>>();
    myNotRegisteredInternalActionIds = new ArrayList<String>();
    myActionListeners = new ArrayList<AnActionListener>();
    myCachedActionListeners = null;
    myKeymapManager = keymapManager;
    myDataManager = dataManager;
  }

  public void initComponent() {}

  public void disposeComponent() {
    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }
  }

  public void addTimerListener(int delay, final TimerListener listener) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (myTimer == null) {
      myTimer = new MyTimer();
      myTimer.start();
    }

    myTimer.addTimerListener(listener);
  }

  public void removeTimerListener(TimerListener listener) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    LOG.assertTrue(myTimer != null);

    myTimer.removeTimerListener(listener);
  }

  public ActionPopupMenu createActionPopupMenu(String place, @NotNull ActionGroup group) {
    return new ActionPopupMenuImpl(place, group, this);
  }

  public ActionToolbar createActionToolbar(final String place, final ActionGroup group, final boolean horizontal) {
    return new ActionToolbarImpl(place, group, horizontal, myDataManager, this, (KeymapManagerEx)myKeymapManager);
  }


  public void readExternal(Element element) {
    final ClassLoader classLoader = getClass().getClassLoader();
    for (final Object o : element.getChildren()) {
      Element children = (Element)o;
      if (ACTIONS_ELEMENT_NAME.equals(children.getName())) {
        processActionsElement(children, classLoader, null);
      }
    }
    registerPluginActions();
  }

  private void registerPluginActions() {
    final Application app = ApplicationManager.getApplication();
    final IdeaPluginDescriptor[] plugins = app.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManager.shouldSkipPlugin(plugin)) continue;
      final List<Element> elementList = plugin.getActionsDescriptionElements();
      if (elementList != null) {
        for (Element e : elementList) {
          processActionsChildElement(plugin.getPluginClassLoader(), plugin.getPluginId(), e);
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  public AnAction getAction(@NotNull String id) {
    return getActionImpl(id, false);
  }

  private AnAction getActionImpl(String id, boolean canReturnStub) {
    synchronized (myLock) {
      AnAction action = (AnAction)myId2Action.get(id);
      if (!canReturnStub && action instanceof ActionStub) {
        action = convert((ActionStub)action);
      }
      return action;
    }
  }

  /**
   * Converts action's stub to normal action.
   */
  private AnAction convert(ActionStub stub) {
    LOG.assertTrue(myAction2Id.contains(stub));
    myAction2Id.remove(stub);

    LOG.assertTrue(myId2Action.contains(stub.getId()));

    AnAction action = (AnAction)myId2Action.remove(stub.getId());
    LOG.assertTrue(action != null);
    LOG.assertTrue(action.equals(stub));

    Object obj;
    String className = stub.getClassName();
    try {
      Constructor<?> constructor = Class.forName(className, true, stub.getLoader()).getDeclaredConstructor();
      constructor.setAccessible(true);
      obj = constructor.newInstance();
    }
    catch (ClassNotFoundException e) {
      PluginId pluginId = stub.getPluginId();
      if (pluginId != null) {
        throw new PluginException("class with name \"" + className + "\" not found", e, pluginId);
      }
      else {
        throw new IllegalStateException("class with name \"" + className + "\" not found");
      }
    }
    catch(UnsupportedClassVersionError e) {
      PluginId pluginId = stub.getPluginId();
      if (pluginId != null) {
        throw new PluginException(e, pluginId);
      }
      else {
        throw new IllegalStateException(e);
      }
    }
    catch (Exception e) {
      PluginId pluginId = stub.getPluginId();
      if (pluginId != null) {
        throw new PluginException("cannot create class \"" + className + "\"", e, pluginId);
      }
      else {
        throw new IllegalStateException("cannot create class \"" + className + "\"", e);
      }
    }

    if (!(obj instanceof AnAction)) {
      throw new IllegalStateException("class with name \"" + className + "\" should be instance of " + AnAction.class.getName());
    }

    AnAction anAction = (AnAction)obj;
    stub.initAction(anAction);
    anAction.getTemplatePresentation().setText(stub.getText());
    String iconPath = stub.getIconPath();
    if (iconPath != null) {
      setIconFromClass(anAction.getClass(), iconPath, stub.getClassName(), anAction.getTemplatePresentation(), stub.getPluginId());
    }

    myId2Action.put(stub.getId(), obj);
    myAction2Id.put(obj, stub.getId());

    return anAction;
  }

  public String getId(@NotNull AnAction action) {
    LOG.assertTrue(!(action instanceof ActionStub));
    synchronized (myLock) {
      return myAction2Id.get(action);
    }
  }

  public String[] getActionIds(@NotNull String idPrefix) {
    synchronized (myLock) {
      ArrayList<String> idList = new ArrayList<String>();
      for (String id : myId2Action.keySet()) {
        if (id.startsWith(idPrefix)) {
          idList.add(id);
        }
      }
      return idList.toArray(new String[idList.size()]);
    }
  }

  public boolean isGroup(@NotNull String actionId) {
    return getActionImpl(actionId, true) instanceof ActionGroup;
  }

  public JComponent createButtonToolbar(final String actionPlace, final ActionGroup messageActionGroup) {
    return new ButtonToolbarImpl(actionPlace, messageActionGroup, myDataManager, this);
  }

  public AnAction getActionOrStub(String id) {
    return getActionImpl(id, true);
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses
   *         of <code>AnAction</code>.
   */
  @Nullable
  private AnAction processActionElement(Element element, final ClassLoader loader, PluginId pluginId) {
    final Application app = ApplicationManager.getApplication();
    final IdeaPluginDescriptor plugin = app.getPlugin(pluginId);
    @NonNls final String resBundleName = plugin != null ? plugin.getResourceBundleBaseName() : ACTIONS_BUNDLE;
    ResourceBundle bundle = null;
    if (resBundleName != null) {
      bundle = getBundle(loader, resBundleName);
    }

    if (!ACTION_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null || className.length() == 0) {
      reportActionError(pluginId, "action element should have specified \"class\" attribute");
      return null;
    }
    // read ID and register loaded action
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null || id.length() == 0) {
      reportActionError(pluginId, "ID of the action cannot be an empty string");
      return null;
    }
    if (Boolean.valueOf(element.getAttributeValue(INTERNAL_ATTR_NAME)).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
      myNotRegisteredInternalActionIds.add(id);
      return null;
    }

    String text = loadTextForElement(element, bundle, id, ACTION_ELEMENT_NAME);

    String iconPath = element.getAttributeValue(ICON_ATTR_NAME);

    if (text == null) {
      @NonNls String message = "'text' attribute is mandatory (action ID=" + id + ";" +
                               (plugin == null ? "" : " plugin path: "+plugin.getPath()) + ")";
      reportActionError(pluginId, message);
      return null;
    }

    ActionStub stub = new ActionStub(className, id, text, loader, pluginId, iconPath);
    Presentation presentation = stub.getTemplatePresentation();
    presentation.setText(text);

    // description

    presentation.setDescription(loadDescriptionForElement(element, bundle, id, ACTION_ELEMENT_NAME));

    // process all links and key bindings if any
    for (final Object o : element.getChildren()) {
      Element e = (Element)o;
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(e.getName())) {
        processAddToGroupNode(stub, e, pluginId);
      }
      else if (SHORTCUT_ELEMENT_NAME.equals(e.getName())) {
        processKeyboardShortcutNode(e, id, pluginId);
      }
      else if (MOUSE_SHORTCUT_ELEMENT_NAME.equals(e.getName())) {
        processMouseShortcutNode(e, id, pluginId);
      }
      else {
        reportActionError(pluginId, "unexpected name of element \"" + e.getName() + "\"");
        return null;
      }
    }
    if (element.getAttributeValue(USE_SHORTCUT_OF_ATTR_NAME) != null) {
      ((KeymapManagerEx)myKeymapManager).bindShortcuts(element.getAttributeValue(USE_SHORTCUT_OF_ATTR_NAME), id);
    }

    // register action
    registerAction(id, stub, pluginId);
    return stub;
  }

  private static void setIcon(@Nullable final String iconPath, final String className, final ClassLoader loader, final Presentation presentation,
                              final PluginId pluginId) {
    if (iconPath == null) return;

    try {
      final Class actionClass = Class.forName(className, true, loader);
      setIconFromClass(actionClass, iconPath, className, presentation, pluginId);
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
      reportActionError(pluginId, "class with name \"" + className + "\" not found");
    }
    catch (NoClassDefFoundError e) {
      LOG.error(e);
      reportActionError(pluginId, "class with name \"" + className + "\" not found");
    }
  }

  private static void setIconFromClass(@NotNull final Class actionClass, @NotNull final String iconPath, final String className,
                                       final Presentation presentation, final PluginId pluginId) {
    //try to find icon in idea class path
    final Icon icon = IconLoader.findIcon(iconPath, actionClass);
    if (icon == null) {
     reportActionError(pluginId, "Icon cannot be found in '" + iconPath + "', action class='" + className + "'");
    }
    else {
      presentation.setIcon(icon);
    }
  }

  private static String loadDescriptionForElement(final Element element, final ResourceBundle bundle, final String id, String elementType) {
    final String value = element.getAttributeValue(DESCRIPTION);
    if (bundle != null) {
      @NonNls final String key = elementType + "." + id + ".description";
      return CommonBundle.messageOrDefault(bundle, key, value == null ? "" : value);
    } else {
      return value;
    }
  }

  private static String loadTextForElement(final Element element, final ResourceBundle bundle, final String id, String elementType) {
    final String value = element.getAttributeValue(TEXT_ATTR_NAME);
    return CommonBundle.messageOrDefault(bundle, elementType + "." + id + "." + TEXT_ATTR_NAME, value == null ? "" : value);
  }

  private AnAction processGroupElement(Element element, final ClassLoader loader, PluginId pluginId) {
    final Application app = ApplicationManager.getApplication();
    final IdeaPluginDescriptor plugin = app.getPlugin(pluginId);
    @NonNls final String resBundleName = plugin != null ? plugin.getResourceBundleBaseName() : ACTIONS_BUNDLE;
    ResourceBundle bundle = null;
    if (resBundleName != null) {
      bundle = getBundle(loader, resBundleName);
    }

    if (!GROUP_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null) { // use default group if class isn't specified
      className = DefaultActionGroup.class.getName();
    }
    try {
      Class aClass = Class.forName(className, true, loader);
      Object obj = new ConstructorInjectionComponentAdapter(className, aClass).getComponentInstance(ApplicationManager.getApplication().getPicoContainer());

      if (!(obj instanceof ActionGroup)) {
        reportActionError(pluginId, "class with name \"" + className + "\" should be instance of " + ActionGroup.class.getName());
        return null;
      }
      if (element.getChildren().size() != element.getChildren(ADD_TO_GROUP_ELEMENT_NAME).size() ) {  //
        if (!(obj instanceof DefaultActionGroup)) {
          reportActionError(pluginId, "class with name \"" + className + "\" should be instance of " + DefaultActionGroup.class.getName() +
                                      " because there are children specified");
          return null;
        }
      }
      ActionGroup group = (ActionGroup)obj;
      // read ID and register loaded group
      String id = element.getAttributeValue(ID_ATTR_NAME);
      if (id != null && id.length() == 0) {
        reportActionError(pluginId, "ID of the group cannot be an empty string");
        return null;
      }
      if (Boolean.valueOf(element.getAttributeValue(INTERNAL_ATTR_NAME)).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
        myNotRegisteredInternalActionIds.add(id);
        return null;
      }

      if (id != null) {
        registerAction(id, group);
      }
      // text
      Presentation presentation = group.getTemplatePresentation();
      String text = loadTextForElement(element, bundle, id, GROUP_ELEMENT_NAME);
      presentation.setText(text);
      // description
      String description = loadDescriptionForElement(element, bundle, id, GROUP_ELEMENT_NAME);
      presentation.setDescription(description);
      // icon
      setIcon(element.getAttributeValue(ICON_ATTR_NAME), className, loader, presentation, pluginId);
      // popup
      String popup = element.getAttributeValue(POPUP_ATTR_NAME);
      if (popup != null) {
        group.setPopup(Boolean.valueOf(popup).booleanValue());
      }
      // process all group's children. There are other groups, actions, references and links
      for (final Object o : element.getChildren()) {
        Element child = (Element)o;
        String name = child.getName();
        if (ACTION_ELEMENT_NAME.equals(name)) {
          AnAction action = processActionElement(child, loader, pluginId);
          if (action != null) {
            assertActionIsGroupOrStub(action);
            ((DefaultActionGroup)group).add(action, this);
          }
        }
        else if (SEPARATOR_ELEMENT_NAME.equals(name)) {
          processSeparatorNode((DefaultActionGroup)group, child, pluginId);
        }
        else if (GROUP_ELEMENT_NAME.equals(name)) {
          AnAction action = processGroupElement(child, loader, pluginId);
          if (action != null) {
            ((DefaultActionGroup)group).add(action, this);
          }
        }
        else if (ADD_TO_GROUP_ELEMENT_NAME.equals(name)) {
          processAddToGroupNode(group, child, pluginId);
        }
        else if (REFERENCE_ELEMENT_NAME.equals(name)) {
          AnAction action = processReferenceElement(child, pluginId);
          if (action != null) {
            ((DefaultActionGroup)group).add(action, this);
          }
        }
        else {
          reportActionError(pluginId, "unexpected name of element \"" + name + "\n");
          return null;
        }
      }
      return group;
    }
    catch (ClassNotFoundException e) {
      reportActionError(pluginId, "class with name \"" + className + "\" not found");
      return null;
    }
    catch (NoClassDefFoundError e) {
      reportActionError(pluginId, "class with name \"" + e.getMessage() + "\" not found");
      return null;
    }
    catch(UnsupportedClassVersionError e) {
      reportActionError(pluginId, "unsupported class version for " + className);
      return null;
    }
    catch (Exception e) {
      final String message = "cannot create class \"" + className + "\"";
      if (pluginId == null) {
        LOG.error(message, e);
      }
      else {
        LOG.error(new PluginException(message, e, pluginId));
      }
      return null;
    }
  }

  private void processReferenceNode(final Element element, final PluginId pluginId) {
    final AnAction action = processReferenceElement(element, pluginId);

    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.getName())) {
        processAddToGroupNode(action, child, pluginId);
      }
    }
  }

  private static Map<String, ResourceBundle> ourBundlesCache = new HashMap<String, ResourceBundle>();

  private static ResourceBundle getBundle(final ClassLoader loader, final String resBundleName) {

    if (ourBundlesCache.containsKey(resBundleName)) {
      return ourBundlesCache.get(resBundleName);
    }

    final ResourceBundle bundle = ResourceBundle.getBundle(resBundleName, Locale.getDefault(), loader);

    ourBundlesCache.put(resBundleName, bundle);

    return bundle;
  }

  /**
   * @param element description of link
   * @param pluginId
   */
  private void processAddToGroupNode(AnAction action, Element element, final PluginId pluginId) {
    // Real subclasses of AnAction should not be here
    if (!(action instanceof Separator)) {
      assertActionIsGroupOrStub(action);
    }

    String actionName = action instanceof ActionStub ? ((ActionStub)action).getClassName() : action.getClass().getName();

    if (!ADD_TO_GROUP_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    String groupId = element.getAttributeValue(GROUPID_ATTR_NAME);
    if (groupId == null || groupId.length() == 0) {
      reportActionError(pluginId, actionName + ": attribute \"group-id\" should be defined");
      return;
    }
    AnAction parentGroup = getActionImpl(groupId, true);
    if (parentGroup == null) {
      reportActionError(pluginId, actionName + ": action with id \"" + groupId + "\" isn't registered; action will be added to the \"Other\" group");
      parentGroup = getActionImpl(IdeActions.GROUP_OTHER_MENU, true);
    }
    if (!(parentGroup instanceof DefaultActionGroup)) {
      reportActionError(pluginId, actionName + ": action with id \"" + groupId + "\" should be instance of " + DefaultActionGroup.class.getName() +
      " but was " + parentGroup.getClass());
      return;
    }
    String anchorStr = element.getAttributeValue(ANCHOR_ELEMENT_NAME);
    if (anchorStr == null) {
      reportActionError(pluginId, actionName + ": attribute \"anchor\" should be defined");
      return;
    }
    Anchor anchor;
    if (FIRST.equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.FIRST;
    }
    else if (LAST.equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.LAST;
    }
    else if (BEFORE.equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.BEFORE;
    }
    else if (AFTER.equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.AFTER;
    }
    else {
      reportActionError(pluginId, actionName + ": anchor should be one of the following constants: \"first\", \"last\", \"before\" or \"after\"");
      return;
    }
    String relativeToActionId = element.getAttributeValue(RELATIVE_TO_ACTION_ATTR_NAME);
    if ((Anchor.BEFORE == anchor || Anchor.AFTER == anchor) && relativeToActionId == null) {
      reportActionError(pluginId, actionName + ": \"relative-to-action\" cannot be null if anchor is \"after\" or \"before\"");
      return;
    }
    ((DefaultActionGroup)parentGroup).add(action, new Constraints(anchor, relativeToActionId), this);
  }

  /**
   * @param parentGroup group wich is the parent of the separator. It can be <code>null</code> in that
   *                    case separator will be added to group described in the <add-to-group ....> subelement.
   * @param element     XML element which represent separator.
   */
  private void processSeparatorNode(DefaultActionGroup parentGroup, Element element, PluginId pluginId) {
    if (!SEPARATOR_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    Separator separator = Separator.getInstance();
    if (parentGroup != null) {
      parentGroup.add(separator, this);
    }
    // try to find inner <add-to-parent...> tag
    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.getName())) {
        processAddToGroupNode(separator, child, pluginId);
      }
    }
  }

  private void processKeyboardShortcutNode(Element element, String actionId, PluginId pluginId) {
    String firstStrokeString = element.getAttributeValue(FIRST_KEYSTROKE_ATTR_NAME);
    if (firstStrokeString == null) {
      reportActionError(pluginId, "\"first-keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    KeyStroke firstKeyStroke = getKeyStroke(firstStrokeString);
    if (firstKeyStroke == null) {
      reportActionError(pluginId, "\"first-keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    KeyStroke secondKeyStroke = null;
    String secondStrokeString = element.getAttributeValue(SECOND_KEYSTROKE_ATTR_NAME);
    if (secondStrokeString != null) {
      secondKeyStroke = getKeyStroke(secondStrokeString);
      if (secondKeyStroke == null) {
        reportActionError(pluginId, "\"second-keystroke\" attribute has invalid value for action with id=" + actionId);
        return;
      }
    }

    String keymapName = element.getAttributeValue(KEYMAP_ATTR_NAME);
    if (keymapName == null || keymapName.trim().length() == 0) {
      reportActionError(pluginId, "attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = myKeymapManager.getKeymap(keymapName);
    if (keymap == null) {
      reportActionError(pluginId, "keymap \"" + keymapName + "\" not found");
      return;
    }

    keymap.addShortcut(actionId, new KeyboardShortcut(firstKeyStroke, secondKeyStroke));
  }

  private static void processMouseShortcutNode(Element element, String actionId, PluginId pluginId) {
    String keystrokeString = element.getAttributeValue(KEYSTROKE_ATTR_NAME);
    if (keystrokeString == null || keystrokeString.trim().length() == 0) {
      reportActionError(pluginId, "\"keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    MouseShortcut shortcut;
    try {
      shortcut = KeymapUtil.parseMouseShortcut(keystrokeString);
    }
    catch (Exception ex) {
      reportActionError(pluginId, "\"keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    String keymapName = element.getAttributeValue(KEYMAP_ATTR_NAME);
    if (keymapName == null || keymapName.length() == 0) {
      reportActionError(pluginId, "attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = KeymapManager.getInstance().getKeymap(keymapName);
    if (keymap == null) {
      reportActionError(pluginId, "keymap \"" + keymapName + "\" not found");
      return;
    }

    keymap.addShortcut(actionId, shortcut);
  }

  @Nullable
  private AnAction processReferenceElement(Element element, PluginId pluginId) {
    if (!REFERENCE_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String ref = element.getAttributeValue(REF_ATTR_NAME);

    if (ref==null) {
      // support old style references by id
      ref = element.getAttributeValue(ID_ATTR_NAME);
    }

    if (ref == null || ref.length() == 0) {
      reportActionError(pluginId, "ID of reference element should be defined");
      return null;
    }

    AnAction action = getActionImpl(ref, true);

    if (action == null) {
      if (!myNotRegisteredInternalActionIds.contains(ref)) {
        reportActionError(pluginId, "action specified by reference isn't registered (ID=" + ref + ")");
      }
      return null;
    }
    assertActionIsGroupOrStub(action);
    return action;
  }

  private void processActionsElement(Element element, ClassLoader loader, PluginId pluginId) {
    if (!ACTIONS_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    synchronized (myLock) {
      for (final Object o : element.getChildren()) {
        Element child = (Element)o;
        processActionsChildElement(loader, pluginId, child);
      }
    }
  }

  private void processActionsChildElement(final ClassLoader loader, final PluginId pluginId, final Element child) {
    String name = child.getName();
    if (ACTION_ELEMENT_NAME.equals(name)) {
      AnAction action = processActionElement(child, loader, pluginId);
      if (action != null) {
        assertActionIsGroupOrStub(action);
      }
    }
    else if (GROUP_ELEMENT_NAME.equals(name)) {
      processGroupElement(child, loader, pluginId);
    }
    else if (SEPARATOR_ELEMENT_NAME.equals(name)) {
      processSeparatorNode(null, child, pluginId);
    }
    else if (REFERENCE_ELEMENT_NAME.equals(name)) {
      processReferenceNode(child, pluginId);
    }
    else {
      reportActionError(pluginId, "unexpected name of element \"" + name + "\n");
    }
  }

  private static void assertActionIsGroupOrStub(final AnAction action) {
    if (!(action instanceof ActionGroup || action instanceof ActionStub)) {
      LOG.assertTrue(false, "Action : "+action + "; class: "+action.getClass());
    }
  }

  public void registerAction(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId) {
    synchronized (myLock) {
      if (myId2Action.containsKey(actionId)) {
        reportActionError(pluginId, "action with the ID \"" + actionId + "\" was already registered. Action being registered is " + action.toString() + 
                                    "; Registered action is " +
                                       myId2Action.get(actionId) + getPluginInfo(pluginId));
        return;
      }
      if (myAction2Id.containsKey(action)) {
        reportActionError(pluginId, "action was already registered for another ID. ID is " + myAction2Id.get(action) +
                                    getPluginInfo(pluginId));
        return;
      }
      myId2Action.put(actionId, action);
      myId2Index.put(actionId, myRegisteredActionsCount++);
      myAction2Id.put(action, actionId);
      if (pluginId != null && !(action instanceof ActionGroup)){
        THashSet<String> pluginActionIds = myPlugin2Id.get(pluginId);
        if (pluginActionIds == null){
          pluginActionIds = new THashSet<String>();
          myPlugin2Id.put(pluginId, pluginActionIds);
        }
        pluginActionIds.add(actionId);
      }
      action.registerCustomShortcutSet(new ProxyShortcutSet(actionId, myKeymapManager), null);
    }
  }

  private static void reportActionError(final PluginId pluginId, @NonNls final String message) {
    if (pluginId == null) {
      LOG.error(message);
    }
    else {
      LOG.error(new PluginException(message, null, pluginId));
    }
  }

  @NonNls
  private static String getPluginInfo(@Nullable PluginId id) {
    if (id != null) {
      final IdeaPluginDescriptor plugin = ApplicationManager.getApplication().getPlugin(id);
      if (plugin != null) {
        String name = plugin.getName();
        if (name == null) {
          name = id.getIdString();
        }
        return " Plugin: " + name;
      }
    }
    return "";
  }

  public void registerAction(@NotNull String actionId, @NotNull AnAction action) {
    registerAction(actionId, action, null);
  }

  public void unregisterAction(@NotNull String actionId) {
    synchronized (myLock) {
      if (!myId2Action.containsKey(actionId)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("action with ID " + actionId + " wasn't registered");
          return;
        }
      }
      AnAction oldValue = (AnAction)myId2Action.remove(actionId);
      myAction2Id.remove(oldValue);
      myId2Index.remove(actionId);
      for (PluginId pluginName : myPlugin2Id.keySet()) {
        final THashSet<String> pluginActions = myPlugin2Id.get(pluginName);
        if (pluginActions != null) {
          pluginActions.remove(actionId);
        }
      }
    }
  }

  @NotNull
  public String getComponentName() {
    final String platformPrefix = System.getProperty("idea.platform.prefix");
    return platformPrefix != null ? platformPrefix + "ActionManager" : "ActionManager";
  }

  public Comparator<String> getRegistrationOrderComparator() {
    return new Comparator<String>() {
      public int compare(String id1, String id2) {
        return myId2Index.get(id1) - myId2Index.get(id2);
      }
    };
  }

  public String[] getPluginActions(PluginId pluginName) {
    if (myPlugin2Id.containsKey(pluginName)){
      final THashSet<String> pluginActions = myPlugin2Id.get(pluginName);
      return pluginActions.toArray(new String[pluginActions.size()]);
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void addActionPopup(final ActionPopupMenuImpl menu) {
    myPopups.add(menu);
  }

  public void removeActionPopup(final ActionPopupMenuImpl menu) {
    final boolean removed = myPopups.remove(menu);
    if (removed && myPopups.size() == 0) {
      flushActionPerformed();
    }
  }

  public void queueActionPerformedEvent(final AnAction action, DataContext context) {
    if (myPopups.size() > 0) {
      myQueuedNotifications.put(action, context);
    } else {
      fireAfterActionPerformed(action, context);
    }
  }


  public boolean isActionPopupStackEmpty() {
    return myPopups.size() == 0;
  }

  private void flushActionPerformed() {
    final Set<AnAction> actions = myQueuedNotifications.keySet();
    for (final AnAction eachAction : actions) {
      final DataContext eachContext = myQueuedNotifications.get(eachAction);
      fireAfterActionPerformed(eachAction, eachContext);
    }
    myQueuedNotifications.clear();
  }

  private AnActionListener[] getActionListeners() {
    if (myCachedActionListeners == null) {
      myCachedActionListeners = myActionListeners.toArray(new AnActionListener[myActionListeners.size()]);
    }

    return myCachedActionListeners;
  }

  public void addAnActionListener(AnActionListener listener) {
    myActionListeners.add(listener);
    myCachedActionListeners = null;
  }

  public void addAnActionListener(final AnActionListener listener, final Disposable parentDisposable) {
    addAnActionListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeAnActionListener(listener);
      }
    });
  }

  public void removeAnActionListener(AnActionListener listener) {
    myActionListeners.remove(listener);
    myCachedActionListeners = null;
  }

  public void fireBeforeActionPerformed(AnAction action, DataContext dataContext) {
    if (action != null) {
      myPrevPerformedActionId = myLastPreformedActionId;
      myLastPreformedActionId = getId(action);
      IdeaLogger.ourLastActionId = myLastPreformedActionId;
    }
    AnActionListener[] listeners = getActionListeners();
    for (AnActionListener listener : listeners) {
      listener.beforeActionPerformed(action, dataContext);
    }
  }

  public void fireAfterActionPerformed(AnAction action, DataContext dataContext) {
    if (action != null) {
      myPrevPerformedActionId = myLastPreformedActionId;
      myLastPreformedActionId = getId(action);
      IdeaLogger.ourLastActionId = myLastPreformedActionId;
    }
    AnActionListener[] listeners = getActionListeners();
    for (AnActionListener listener : listeners) {
      try {
        listener.afterActionPerformed(action, dataContext);
      }
      catch(AbstractMethodError e) {
        // ignore
      }
    }
  }

  public void fireBeforeEditorTyping(char c, DataContext dataContext) {
    myLastTimeEditorWasTypedIn = System.currentTimeMillis();
    AnActionListener[] listeners = getActionListeners();
    for (AnActionListener listener : listeners) {
      listener.beforeEditorTyping(c, dataContext);
    }
  }

  public String getLastPreformedActionId() {
    return myLastPreformedActionId;
  }

  public String getPrevPreformedActionId() {
    return myPrevPerformedActionId;
  }

  public Set<String> getActionIds(){
    return new HashSet<String>(myId2Action.keySet());
  }

  private int myActionsPreloaded = 0;

  public void preloadActions() {
    if (myPreloadActionsRunnable == null) {
      myPreloadActionsRunnable = new Runnable() {
        public void run() {
          doPreloadActions();
        }
      };
      ApplicationManager.getApplication().executeOnPooledThread(myPreloadActionsRunnable);
    }
  }

  private void doPreloadActions() {
    try {
      Thread.sleep(5000); // wait for project initialization to complete
    }
    catch (InterruptedException e) {
      // ignore
    }
    preloadActionGroup(IdeActions.GROUP_EDITOR_POPUP);
    preloadActionGroup(IdeActions.GROUP_EDITOR_TAB_POPUP);
    preloadActionGroup(IdeActions.GROUP_PROJECT_VIEW_POPUP);
    preloadActionGroup(IdeActions.GROUP_MAIN_MENU);
    // TODO anything else?
    LOG.debug("Actions preloading completed");
  }

  public void preloadActionGroup(final String groupId) {
    final AnAction action = getAction(groupId);
    if (action instanceof DefaultActionGroup) {
      preloadActionGroup((DefaultActionGroup) action);
    }
  }

  private void preloadActionGroup(final DefaultActionGroup group) {
    final AnAction[] actions = group.getChildActionsOrStubs(null);
    for (AnAction action : actions) {
      if (action instanceof ActionStub) {
        AnAction convertedAction = null;
        synchronized (myLock) {
          final String id = myAction2Id.get(action);
          if (id != null) {
            convertedAction = convert((ActionStub)action);
          }
        }
        if (convertedAction instanceof PreloadableAction) {
          final PreloadableAction preloadableAction = (PreloadableAction)convertedAction;
          preloadableAction.preload();
        }
        myActionsPreloaded++;
        if (myActionsPreloaded % 10 == 0) {
          try {
            Thread.sleep(300);
          }
          catch (InterruptedException e) {
            // ignore
          }
        }
      }
      else if (action instanceof DefaultActionGroup) {
        preloadActionGroup((DefaultActionGroup) action);
      }
    }
  }

  private class MyTimer extends Timer implements ActionListener {
    private final List<TimerListener> myTimerListeners = Collections.synchronizedList(new ArrayList<TimerListener>());
    private int myLastTimePerformed;

    MyTimer() {
      super(TIMER_DELAY, null);
      addActionListener(this);
      setRepeats(true);
     }

    public void addTimerListener(TimerListener listener){
      myTimerListeners.add(listener);
    }

    public void removeTimerListener(TimerListener listener){
      final boolean removed = myTimerListeners.remove(listener);
      if (!removed) {
        LOG.assertTrue(false, "Unknown listener " + listener);
      }
    }

    public void actionPerformed(ActionEvent e) {
      if (myLastTimeEditorWasTypedIn + UPDATE_DELAY_AFTER_TYPING > System.currentTimeMillis()) {
        return;
      }

      final int lastEventCount = myLastTimePerformed;
      myLastTimePerformed = ActivityTracker.getInstance().getCount();

      if (myLastTimePerformed == lastEventCount) {
        return;
      }

      TimerListener[] listeners = myTimerListeners.toArray(new TimerListener[myTimerListeners.size()]);
      for (TimerListener listener : listeners) {
        runListenerAction(listener);
      }
    }

    private void runListenerAction(final TimerListener listener) {
      ModalityState modalityState = listener.getModalityState();
      if (modalityState == null) return;
      if (!ModalityState.current().dominates(modalityState)) {
        listener.run();
      }
    }
  }
}

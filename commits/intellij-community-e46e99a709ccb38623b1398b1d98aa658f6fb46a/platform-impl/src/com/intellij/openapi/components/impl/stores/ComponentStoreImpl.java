package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.io.fs.IFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

@SuppressWarnings({"deprecation"})
abstract class ComponentStoreImpl implements IComponentStore {

  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentStoreImpl");
  private Map<String, Object> myComponents = Collections.synchronizedMap(new TreeMap<String, Object>());
  private List<SettingsSavingComponent> mySettingsSavingComponents = Collections.synchronizedList(new ArrayList<SettingsSavingComponent>());
  @Nullable private SaveSessionImpl mySession;

  @Deprecated
  @Nullable
  private StateStorage getStateStorage(@NotNull final Storage storageSpec) throws StateStorage.StateStorageException {
    return getStateStorageManager().getStateStorage(storageSpec);
  }

  @Deprecated
  @Nullable
  private StateStorage getOldStorage(final Object component, final String componentName, final StateStorageOperation operation)
      throws StateStorage.StateStorageException {
    return getStateStorageManager().getOldStorage(component, componentName, operation);
  }

  protected StateStorage getDefaultsStorage() {
    throw new UnsupportedOperationException("Method getDefaultsStorage is not supported in " + getClass());
  }

  public void initComponent(@NotNull final Object component) {
    boolean isSerializable = component instanceof JDOMExternalizable ||
                             component instanceof PersistentStateComponent ||
                             component instanceof SettingsSavingComponent;

    if (!isSerializable) return;

    if (component instanceof SettingsSavingComponent) {
      SettingsSavingComponent settingsSavingComponent = (SettingsSavingComponent)component;
      mySettingsSavingComponents.add(settingsSavingComponent);
    }

    final Runnable r = new Runnable() {
      public void run() {
        if (component instanceof PersistentStateComponent) {
          initPersistentComponent((PersistentStateComponent<?>)component);
        }
        else if (component instanceof JDOMExternalizable) {
          initJdomExternalizable((JDOMExternalizable)component);
        }
      }
    };

    final ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
    if (applicationEx.isUnitTestMode()) {
      r.run();
    }
    else {
      applicationEx.runReadAction(r);
    }
  }

  public boolean isSaving() {
    return mySession != null;
  }


  @NotNull
  public SaveSession startSave() throws IOException {
    try {
      final ComponentStoreImpl.SaveSessionImpl session = createSaveSession();
      try {
        session.commit();
      }
      catch (Throwable e) {
        session.reset();
        LOG.info(e);
        throw new IOException(e.getMessage());
      }
      mySession = session;
      return mySession;
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }

  protected SaveSessionImpl createSaveSession() throws StateStorage.StateStorageException {
    return new SaveSessionImpl();
  }

  public void finishSave(@NotNull final SaveSession saveSession) {
    assert mySession == saveSession;
    mySession.finishSave();
    mySession = null;
  }

  private <T> void commitPersistentComponent(@NotNull final PersistentStateComponent<T> persistentStateComponent,
                                             @NotNull StateStorageManager.ExternalizationSession session) {
    Storage[] storageSpecs = getComponentStorageSpecs(persistentStateComponent, StateStorageOperation.WRITE);

    T state = persistentStateComponent.getState();
    if (state != null) {
      session
        .setState(storageSpecs, persistentStateComponent, getComponentName(persistentStateComponent), state);
    }
  }

  private static void commitJdomExternalizable(@NotNull final JDOMExternalizable component,
                                               @NotNull StateStorageManager.ExternalizationSession session) {
    final String componentName = getComponentName(component);

    session.setStateInOldStorage(component, componentName, component);
  }

  void initJdomExternalizable(@NotNull JDOMExternalizable component) {
    final String componentName = getComponentName(component);

    myComponents.put(componentName, component);

    if (optimizeTestLoading()) return;

    loadJdomDefaults(component, componentName);

    Element element = null;
    StateStorage stateStorage = getOldStorage(component, componentName, StateStorageOperation.READ);

    if (stateStorage == null) return;
    element = getJdomState(component, componentName, stateStorage);

    if (element == null) return;

    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Loading configuration for " + component.getClass());
      }
      component.readExternal(element);
    }
    catch (InvalidDataException e) {
      throw new InvalidComponentDataException(e);
    }
  }

  private static String getComponentName(@NotNull final JDOMExternalizable component) {
    if ((component instanceof BaseComponent)) {
      return ((BaseComponent)component).getComponentName();
    }
    else {
      return component.getClass().getName();
    }
  }

  private void loadJdomDefaults(@NotNull final Object component, final String componentName) {
    try {
      StateStorage defaultsStorage = getDefaultsStorage();
      if (defaultsStorage == null) return;

      Element defaultState = getJdomState(component, componentName, defaultsStorage);
      if (defaultState == null) return;

      ((JDOMExternalizable)component).readExternal(defaultState);
    }
    catch (Exception e) {
      LOG.error("Cannot load defaults for " + component.getClass(), e);
    }
  }

  @Nullable
  private Element getJdomState(final Object component, final String componentName, @NotNull final StateStorage defaultsStorage)
      throws StateStorage.StateStorageException {
    ComponentRoamingManager roamingManager = ComponentRoamingManager.getInstance();
    if (!roamingManager.typeSpecified(componentName)) {
      if (component instanceof RoamingTypeDisabled) {
         roamingManager.setRoamingType(componentName, RoamingType.DISABLED);
      }
      else if (component instanceof RoamingTypePerPlatform) {
        roamingManager.setRoamingType(componentName, RoamingType.PER_PLATFORM);
      }
      /*else {
        roamingManager.setRoamingType(componentName, RoamingType.PER_USER);
      }*/
    }
    return defaultsStorage.getState(component, componentName, Element.class, null);
  }

  private <T> void initPersistentComponent(@NotNull final PersistentStateComponent<T> component) {
    final String name = getComponentName(component);

    RoamingType roamingTypeFromComponent = getRoamingType(component);
    ComponentRoamingManager roamingManager = ComponentRoamingManager.getInstance();
    if (!roamingManager.typeSpecified(name)) {
      roamingManager.setRoamingType(name, roamingTypeFromComponent);
    }

    myComponents.put(name, component);
    if (optimizeTestLoading()) return;

    Class<T> stateClass = getComponentStateClass(component);

    T state = null;
    //todo: defaults merging

    final StateStorage defaultsStorage = getDefaultsStorage();
    if (defaultsStorage != null) {
      state = defaultsStorage.getState(component, name, stateClass, null);
    }

    Storage[] storageSpecs = getComponentStorageSpecs(component, StateStorageOperation.READ);

    for (Storage storageSpec : storageSpecs) {
      StateStorage stateStorage = getStateStorage(storageSpec);
      if (stateStorage == null || !stateStorage.hasState(component, name, stateClass)) continue;
      state = stateStorage.getState(component, name, stateClass, state);
    }

    if (state != null) {
      component.loadState(state);
    }
  }

  private RoamingType getRoamingType(final PersistentStateComponent component) {
    final State stateSpec = getStateSpec(component);
    assert stateSpec != null;

    return stateSpec.roamingType();

  }

  @NotNull
  private static <T> Class<T> getComponentStateClass(@NotNull final PersistentStateComponent<T> persistentStateComponent) {
    final Class persistentStateComponentClass = PersistentStateComponent.class;
    Class componentClass = persistentStateComponent.getClass();

    nextSuperClass:
    while (true) {
      final Class[] interfaces = ReflectionCache.getInterfaces(componentClass);

      for (Class anInterface : interfaces) {
        if (anInterface.equals(persistentStateComponentClass)) {
          break nextSuperClass;
        }
      }

      componentClass = componentClass.getSuperclass();
    }

    final Type type = ReflectionUtil.resolveVariable(persistentStateComponentClass.getTypeParameters()[0], componentClass);

    //noinspection unchecked
    return (Class<T>)ReflectionUtil.getRawType(type);
  }

  private static String getComponentName(@NotNull final PersistentStateComponent<?> persistentStateComponent) {
    final State stateSpec = getStateSpec(persistentStateComponent);
    if (stateSpec == null) {
      LOG.assertTrue(false, "Null state spec for " + persistentStateComponent);
    }
    return stateSpec.name();
  }

  private static <T> State getStateSpec(@NotNull final PersistentStateComponent<T> persistentStateComponent) {
    final Class<? extends PersistentStateComponent> aClass = persistentStateComponent.getClass();
    final State stateSpec = aClass.getAnnotation(State.class);
    assert stateSpec != null : "No @State annotation found in " + aClass;
    return stateSpec;
  }


  @NotNull
  protected <T> Storage[] getComponentStorageSpecs(@NotNull final PersistentStateComponent<T> persistentStateComponent,
                                                   final StateStorageOperation operation) throws StateStorage.StateStorageException {
    final State stateSpec = getStateSpec(persistentStateComponent);

    final Storage[] storages = stateSpec.storages();

    if (storages.length == 1) return storages;

    assert storages.length > 0;


    final Class<StorageAnnotationsDefaultValues.NullStateStorageChooser> defaultClass =
        StorageAnnotationsDefaultValues.NullStateStorageChooser.class;

    final Class<? extends StateStorageChooser> storageChooserClass = stateSpec.storageChooser();
    final StateStorageChooser defaultStateStorageChooser = getDefaultStateStorageChooser();
    assert storageChooserClass != defaultClass || defaultStateStorageChooser != null : "State chooser not specified for: " +
                                                                                       persistentStateComponent.getClass();

    if (storageChooserClass == defaultClass) {
      return defaultStateStorageChooser.selectStorages(storages, persistentStateComponent, operation);
    }
    else {
      try {
        //noinspection unchecked
        final StateStorageChooser<PersistentStateComponent<T>> storageChooser = storageChooserClass.newInstance();
        return storageChooser.selectStorages(storages, persistentStateComponent, operation);
      }
      catch (InstantiationException e) {
        throw new StateStorage.StateStorageException(e);
      }
      catch (IllegalAccessException e) {
        throw new StateStorage.StateStorageException(e);
      }
    }
  }

  protected boolean optimizeTestLoading() {
    return false;
  }

  @Nullable
  protected StateStorageChooser getDefaultStateStorageChooser() {
    return null;
  }

  protected class SaveSessionImpl implements SaveSession {
    protected StateStorageManager.SaveSession myStorageManagerSaveSession;

    public SaveSessionImpl() {
      ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
    }

    public Collection<String> getUsedMacros() throws StateStorage.StateStorageException {
      return myStorageManagerSaveSession.getUsedMacros();
    }

    public List<IFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException {
      try {
        return myStorageManagerSaveSession.getAllStorageFilesToSave();
      }
      catch (StateStorage.StateStorageException e) {
        throw new IOException(e.getMessage());
      }
    }

    public SaveSession save() throws IOException {
      try {
        final SettingsSavingComponent[] settingsComponents =
            mySettingsSavingComponents.toArray(new SettingsSavingComponent[mySettingsSavingComponents.size()]);

        for (SettingsSavingComponent settingsSavingComponent : settingsComponents) {
          settingsSavingComponent.save();
        }


        myStorageManagerSaveSession.save();
      }
      catch (StateStorage.StateStorageException e) {
        LOG.info(e);
        throw new IOException(e.getMessage());
      }

      return this;
    }

    public void finishSave() {
      getStateStorageManager().finishSave(myStorageManagerSaveSession);
      myStorageManagerSaveSession = null;
      ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
      mySession = null;
    }

    public void reset() {
      getStateStorageManager().reset();
      myStorageManagerSaveSession = null;
      ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
      mySession = null;
    }

    protected void commit() throws StateStorage.StateStorageException {
      final StateStorageManager storageManager = getStateStorageManager();

      final StateStorageManager.ExternalizationSession session = storageManager.startExternalization();

      final String[] names = myComponents.keySet().toArray(new String[myComponents.keySet().size()]);

      for (String name : names) {
        Object component = myComponents.get(name);
        if (component instanceof PersistentStateComponent) {
          commitPersistentComponent((PersistentStateComponent<?>)component, session);
        }
        else if (component instanceof JDOMExternalizable) {
          commitJdomExternalizable((JDOMExternalizable)component, session);
        }
      }
      myStorageManagerSaveSession = storageManager.startSave(session);
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
      return myStorageManagerSaveSession.analyzeExternalChanges(changedFiles);
    }

    public List<IFile> getAllStorageFiles(final boolean includingSubStructures) {
      return myStorageManagerSaveSession.getAllStorageFiles();
    }

  }

  protected boolean isReloadPossible(final Set<String> componentNames) {
    for (String componentName : componentNames) {
      final Object component = myComponents.get(componentName);

      if (component != null) {
        if (!(component instanceof PersistentStateComponent)) return false;

        final State stateSpec = getStateSpec((PersistentStateComponent<? extends Object>)component);
        if (stateSpec == null || !stateSpec.reloadable()) return false;
      }
    }

    return true;
  }

  protected void reinitComponents(final Set<String> componentNames) {
    for (String componentName : componentNames) {
      final PersistentStateComponent component = (PersistentStateComponent)myComponents.get(componentName);
      if (component != null) {
        initPersistentComponent(component);
      }
    }
  }

  protected void doReload(final Set<Pair<VirtualFile, StateStorage>> changedFiles, @NotNull final Set<String> componentNames)
      throws StateStorage.StateStorageException {
    for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
      assert pair != null;
      final StateStorage storage = pair.second;
      assert storage != null : "Null storage for: " + pair.first;
      storage.reload(componentNames);
    }
  }
}

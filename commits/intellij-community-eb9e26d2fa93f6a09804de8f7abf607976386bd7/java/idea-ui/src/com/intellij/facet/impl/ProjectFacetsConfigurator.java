/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.facet.impl.ui.FacetTreeModel;
import com.intellij.facet.impl.ui.ProjectConfigurableContext;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ProjectFacetsConfigurator implements FacetsProvider, ModuleEditor.ChangeListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetsConfigurator");
  private final Map<Module, ModifiableFacetModel> myModels = new HashMap<Module, ModifiableFacetModel>();
  private final Map<Facet, FacetEditorImpl> myEditors = new HashMap<Facet, FacetEditorImpl>();
  private final Map<Module, FacetTreeModel> myTreeModels = new HashMap<Module, FacetTreeModel>();
  private final Map<FacetInfo, Facet> myInfo2Facet = new HashMap<FacetInfo, Facet>();
  private final Map<Facet, FacetInfo> myFacet2Info = new HashMap<Facet, FacetInfo>();
  private final Map<Module, UserDataHolder> mySharedModuleData = new HashMap<Module, UserDataHolder>();
  private final Set<Facet> myFacetsToDispose = new HashSet<Facet>();
  private final Set<Facet> myChangedFacets = new HashSet<Facet>();
  private final Set<Facet> myCreatedFacets = new HashSet<Facet>();
  private final StructureConfigurableContext myContext;
  private final Project myProject;
  private UserDataHolderBase myProjectData = new UserDataHolderBase();

  public ProjectFacetsConfigurator(final StructureConfigurableContext context, Project project) {
    myContext = context;
    myProject = project;
  }

  public List<Facet> removeFacet(Facet facet) {
    FacetTreeModel treeModel = getTreeModel(facet.getModule());
    FacetInfo facetInfo = myFacet2Info.get(facet);
    if (facetInfo == null) return Collections.emptyList();

    final List<Facet> removed = new ArrayList<Facet>();
    List<FacetInfo> children = treeModel.getChildren(facetInfo);
    for (FacetInfo child : children) {
      Facet childInfo = myInfo2Facet.get(child);
      if (childInfo != null) {
        removed.addAll(removeFacet(childInfo));
      }
    }

    treeModel.removeFacetInfo(facetInfo);
    getOrCreateModifiableModel(facet.getModule()).removeFacet(facet);
    myChangedFacets.remove(facet);
    if (myCreatedFacets.contains(facet)) {
      Disposer.dispose(facet);
    }
    final FacetEditorImpl facetEditor = myEditors.remove(facet);
    if (facetEditor != null) {
      facetEditor.disposeUIResources();
    }
    myFacet2Info.remove(facet);
    myInfo2Facet.remove(facetInfo);
    removed.add(facet);
    return removed;
  }

  public Facet createAndAddFacet(Module module, FacetType<?, ?> type, String name, final @Nullable FacetInfo underlyingFacet) {
    final Facet facet = FacetManager.getInstance(module).createFacet(type, name, myInfo2Facet.get(underlyingFacet));
    myCreatedFacets.add(facet);
    addFacetInfo(facet);
    getOrCreateModifiableModel(module).addFacet(facet);
    return facet;
  }

  public void addFacetInfo(final Facet facet) {
    final FacetInfo exiting = myFacet2Info.get(facet);
    if (exiting != null) {
      LOG.assertTrue(exiting.getName().equals(facet.getName()));
      LOG.assertTrue(exiting.getFacetType().equals(facet.getType()));
      LOG.assertTrue(exiting.getConfiguration().equals(facet.getConfiguration()));
      return;
    }

    FacetInfo info = new FacetInfo(facet.getType(), facet.getName(), facet.getConfiguration(), myFacet2Info.get(facet.getUnderlyingFacet()));
    myFacet2Info.put(facet, info);
    myInfo2Facet.put(info, facet);
    getTreeModel(facet.getModule()).addFacetInfo(info);
  }

  public void addFacetInfos(final Module module) {
    final Facet[] facets = getFacetModel(module).getSortedFacets();
    for (Facet facet : facets) {
      addFacetInfo(facet);
    }
  }

  public void clearMaps() {
    myModels.clear();
    myEditors.clear();
    myTreeModels.clear();
    myInfo2Facet.clear();
    myFacet2Info.clear();
    myChangedFacets.clear();
    mySharedModuleData.clear();
  }

  private boolean isNewFacet(Facet facet) {
    final ModifiableFacetModel model = myModels.get(facet.getModule());
    return model != null && model.isNewFacet(facet);
  }

  @NotNull
  public ModifiableFacetModel getOrCreateModifiableModel(final Module module) {
    ModifiableFacetModel model = myModels.get(module);
    if (model == null) {
      model = FacetManager.getInstance(module).createModifiableModel();
      model.addListener(new ModifiableFacetModel.Listener() {
        public void onChanged() {
          fireFacetModelChanged(module);
        }
      }, null);
      myModels.put(module, model);
    }
    return model;
  }

  @Nullable
  public FacetEditorImpl getEditor(Facet facet) {
    return myEditors.get(facet);
  }
  
  @NotNull
  public FacetEditorImpl getOrCreateEditor(Facet facet) {
    FacetEditorImpl editor = myEditors.get(facet);
    if (editor == null) {
      final Facet underlyingFacet = facet.getUnderlyingFacet();
      final FacetEditorContext parentContext = underlyingFacet != null ? getOrCreateEditor(underlyingFacet).getContext() : null;

      final FacetEditorContext context = createContext(facet, parentContext);
      editor = new FacetEditorImpl(context, facet.getConfiguration());
      editor.getComponent();
      editor.reset();
      myEditors.put(facet, editor);
    }
    return editor;
  }

  protected FacetEditorContext createContext(final @NotNull Facet facet, final @Nullable FacetEditorContext parentContext) {
    Module module = facet.getModule();
    ModulesConfigurator modulesConfigurator = myContext.getModulesConfigurator();
    ModuleEditor moduleEditor = modulesConfigurator.getModuleEditor(module);
    if (moduleEditor == null) {
      LOG.error("ModuleEditor[" + module.getName() + "]==null: disposed = " + module.isDisposed() + ", is in model = "
                + Arrays.asList(modulesConfigurator.getModules()).contains(module));
    }

    final ModuleConfigurationState state = moduleEditor.createModuleConfigurationState();
    return new MyProjectConfigurableContext(facet, parentContext, state);
  }

  private UserDataHolder getSharedModuleData(final Module module) {
    UserDataHolder dataHolder = mySharedModuleData.get(module);
    if (dataHolder == null) {
      dataHolder = new UserDataHolderBase();
      mySharedModuleData.put(module, dataHolder);
    }
    return dataHolder;
  }

  @NotNull
  public FacetModel getFacetModel(Module module) {
    final ModifiableFacetModel model = myModels.get(module);
    if (model != null) {
      return model;
    }
    return FacetManager.getInstance(module);
  }

  public void commitFacets() {
    for (ModifiableFacetModel model : myModels.values()) {
      model.commit();
    }

    for (Map.Entry<Facet, FacetEditorImpl> entry : myEditors.entrySet()) {
      entry.getValue().onFacetAdded(entry.getKey());
    }

    myModels.clear();
    for (Facet facet : myChangedFacets) {
      Module module = facet.getModule();
      if (!module.isDisposed()) {
        module.getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
      }
    }
    myChangedFacets.clear();
  }

  public void resetEditors() {
    for (FacetEditorImpl editor : myEditors.values()) {
      editor.reset();
    }
  }

  public void applyEditors() throws ConfigurationException {
    for (Map.Entry<Facet, FacetEditorImpl> entry : myEditors.entrySet()) {
      final FacetEditorImpl editor = entry.getValue();
      if (editor.isModified()) {
        myChangedFacets.add(entry.getKey());
      }
      editor.apply();
    }
  }

  public boolean isModified() {
    for (ModifiableFacetModel model : myModels.values()) {
      if (model.isModified()) {
        return true;
      }
    }
    for (FacetEditorImpl editor : myEditors.values()) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  public FacetTreeModel getTreeModel(Module module) {
    FacetTreeModel treeModel = myTreeModels.get(module);
    if (treeModel == null) {
      treeModel = new FacetTreeModel();
      myTreeModels.put(module, treeModel);
    }
    return treeModel;
  }

  public FacetInfo getFacetInfo(final Facet facet) {
    return myFacet2Info.get(facet);
  }

  public Facet getFacet(final FacetInfo facetInfo) {
    return myInfo2Facet.get(facetInfo);
  }

  public void disposeEditors() {
    for (Facet facet : myFacetsToDispose) {
      Disposer.dispose(facet);
    }
    myFacetsToDispose.clear();
    myCreatedFacets.clear();

    for (FacetEditorImpl editor : myEditors.values()) {
      editor.disposeUIResources();
    }
    myProjectData = null;
  }

  @NotNull
  public Facet[] getAllFacets(final Module module) {
    return getFacetModel(module).getAllFacets();
  }

  @NotNull
  public <F extends Facet> Collection<F> getFacetsByType(final Module module, final FacetTypeId<F> type) {
    return getFacetModel(module).getFacetsByType(type);
  }

  @Nullable
  public <F extends Facet> F findFacet(final Module module, final FacetTypeId<F> type, final String name) {
    return getFacetModel(module).findFacet(type, name);
  }

  public void moduleStateChanged(final ModifiableRootModel moduleRootModel) {
    Module module = moduleRootModel.getModule();
    Facet[] allFacets = getAllFacets(module);
    for (Facet facet : allFacets) {
      FacetEditorImpl facetEditor = myEditors.get(facet);
      if (facetEditor != null) {
        ((FacetEditorContextBase)facetEditor.getContext()).fireModuleRootsChanged(moduleRootModel);
      }
    }
  }

  private void fireFacetModelChanged(Module module) {
    for (Facet facet : getAllFacets(module)) {
      FacetEditorImpl facetEditor = myEditors.get(facet);
      if (facetEditor != null) {
        ((FacetEditorContextBase)facetEditor.getContext()).fireFacetModelChanged(module);
      }
    }
  }

  private UserDataHolder getProjectData() {
    if (myProjectData == null) {
      myProjectData = new UserDataHolderBase();
    }
    return myProjectData;
  }

  public List<Facet> removeAllFacets(final Module module) {
    List<Facet> facets = new ArrayList<Facet>();
    FacetModel facetModel = getOrCreateModifiableModel(module);
    for (Facet facet : facetModel.getAllFacets()) {
      if (!myCreatedFacets.contains(facet)) {
        myFacetsToDispose.add(facet);
      }
      LOG.assertTrue(facet.getModule().equals(module), module + " expected but " + facet.getModule() + " found");
      facets.addAll(removeFacet(facet));
    }
    mySharedModuleData.remove(module);
    myModels.remove(module);
    return facets;
  }

  private class MyProjectConfigurableContext extends ProjectConfigurableContext {
    private final LibrariesContainer myContainer;

    public MyProjectConfigurableContext(final Facet facet, final FacetEditorContext parentContext, final ModuleConfigurationState state) {
      super(facet, ProjectFacetsConfigurator.this.isNewFacet(facet), parentContext, state,
            ProjectFacetsConfigurator.this.getSharedModuleData(facet.getModule()), getProjectData());
      myContainer = LibrariesContainerFactory.createContainer(facet.getModule().getProject(), myContext);
    }

    public LibrariesContainer getContainer() {
      return myContainer;
    }

  }
}

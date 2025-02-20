/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.packageDependencies;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

@State(
  name="DependencyValidationManager",
  storages= {
    @Storage(
      file = StoragePathMacros.PROJECT_FILE
    ), @Storage( file = StoragePathMacros.PROJECT_CONFIG_DIR + "/scopes/", scheme = StorageScheme.DIRECTORY_BASED,
                                       stateSplitter = DependencyValidationManagerImpl.ScopesStateSplitter.class)}
)
public class DependencyValidationManagerImpl extends DependencyValidationManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packageDependencies.DependencyValidationManagerImpl");

  private final List<DependencyRule> myRules = new ArrayList<DependencyRule>();

  public boolean SKIP_IMPORT_STATEMENTS = false;

  @NonNls private static final String DENY_RULE_KEY = "deny_rule";
  @NonNls private static final String FROM_SCOPE_KEY = "from_scope";
  @NonNls private static final String TO_SCOPE_KEY = "to_scope";
  @NonNls private static final String IS_DENY_KEY = "is_deny";
  @NonNls private static final String UNNAMED_SCOPE = "unnamed_scope";
  @NonNls private static final String VALUE = "value";


  private static final Icon SHARED_SCOPES = AllIcons.Ide.SharedScope;

  private final Map<String, PackageSet> myUnnamedScopes = new HashMap<String, PackageSet>();

  public DependencyValidationManagerImpl(final Project project) {
    super(project);
  }

  @NotNull
  public List<NamedScope> getPredefinedScopes() {
    final List<NamedScope> predefinedScopes = new ArrayList<NamedScope>();
    final CustomScopesProvider[] scopesProviders = myProject.getExtensions(CustomScopesProvider.CUSTOM_SCOPES_PROVIDER);
    if (scopesProviders != null) {
      for (CustomScopesProvider scopesProvider : scopesProviders) {
        predefinedScopes.addAll(scopesProvider.getCustomScopes());
      }
    }
    return predefinedScopes;
  }

  @Override
  public NamedScope getPredefinedScope(String name) {
    final CustomScopesProvider[] scopesProviders = myProject.getExtensions(CustomScopesProvider.CUSTOM_SCOPES_PROVIDER);
    if (scopesProviders != null) {
      for (CustomScopesProvider scopesProvider : scopesProviders) {
        final NamedScope scope = scopesProvider instanceof CustomScopesProviderEx 
                                 ? ((CustomScopesProviderEx)scopesProvider).getCustomScope(name) 
                                 : CustomScopesProviderEx.findPredefinedScope(name, scopesProvider.getCustomScopes());
        if (scope != null) {
          return scope;
        }
      }
    }
    return null;
  }

  public boolean hasRules() {
    return !myRules.isEmpty();
  }

  @Nullable
  public DependencyRule getViolatorDependencyRule(PsiFile from, PsiFile to) {
    for (DependencyRule dependencyRule : myRules) {
      if (dependencyRule.isForbiddenToUse(from, to)) return dependencyRule;
    }

    return null;
  }

  @NotNull
  public DependencyRule[] getViolatorDependencyRules(PsiFile from, PsiFile to) {
    ArrayList<DependencyRule> result = new ArrayList<DependencyRule>();
    for (DependencyRule dependencyRule : myRules) {
      if (dependencyRule.isForbiddenToUse(from, to)) {
        result.add(dependencyRule);
      }
    }
    return result.toArray(new DependencyRule[result.size()]);
  }

  public
  @NotNull
  DependencyRule[] getApplicableRules(PsiFile file) {
    ArrayList<DependencyRule> result = new ArrayList<DependencyRule>();
    for (DependencyRule dependencyRule : myRules) {
      if (dependencyRule.isApplicable(file)) {
        result.add(dependencyRule);
      }
    }
    return result.toArray(new DependencyRule[result.size()]);
  }

  public boolean skipImportStatements() {
    return SKIP_IMPORT_STATEMENTS;
  }

  public void setSkipImportStatements(final boolean skip) {
    SKIP_IMPORT_STATEMENTS = skip;
  }

  public Map<String, PackageSet> getUnnamedScopes() {
    return myUnnamedScopes;
  }

  public DependencyRule[] getAllRules() {
    return myRules.toArray(new DependencyRule[myRules.size()]);
  }

  public void removeAllRules() {
    myRules.clear();
  }

  public void addRule(DependencyRule rule) {
    appendUnnamedScope(rule.getFromScope());
    appendUnnamedScope(rule.getToScope());
    myRules.add(rule);
  }

  @Override
  public void reloadRules() {
    final Element element = new Element("rules_2_reload");
    writeRules(element);
    readRules(element);
  }

  private void appendUnnamedScope(final NamedScope fromScope) {
    if (getScope(fromScope.getName()) == null) {
      final PackageSet packageSet = fromScope.getValue();
      if (packageSet != null && !myUnnamedScopes.containsKey(packageSet.getText())) {
        myUnnamedScopes.put(packageSet.getText(), packageSet);
      }
    }
  }

  public String getDisplayName() {
    return IdeBundle.message("shared.scopes.node.text");
  }

  public Icon getIcon() {
    return SHARED_SCOPES;
  }

  @Override
  public void loadState(final Element element) {
    try {
      DefaultJDOMExternalizer.readExternal(this, element);
    }
    catch (InvalidDataException e) {
      LOG.info(e);
    }
    super.loadState(element);

    myUnnamedScopes.clear();
    final List unnamedScopes = element.getChildren(UNNAMED_SCOPE);
    final PackageSetFactory packageSetFactory = PackageSetFactory.getInstance();
    for (Object unnamedScope : unnamedScopes) {
      try {
        final String packageSet = ((Element)unnamedScope).getAttributeValue(VALUE);
        myUnnamedScopes.put(packageSet, packageSetFactory.compile(packageSet));
      }
      catch (ParsingException e) {
        //skip pattern
      }
    }

    readRules(element);
  }

  private void readRules(Element element) {
    myRules.clear();
    List rules = element.getChildren(DENY_RULE_KEY);
    for (Object rule1 : rules) {
      DependencyRule rule = readRule((Element)rule1);
      if (rule != null) {
        addRule(rule);
      }
    }
  }

  @Override
  public Element getState() {
    Element element = super.getState();
    try {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
    catch (WriteExternalException e) {
      LOG.info(e);
    }
    final List<String> unnamedScopes = new ArrayList<String>(myUnnamedScopes.keySet());
    Collections.sort(unnamedScopes);
    for (final String unnamedScope : unnamedScopes) {
      Element unnamedElement = new Element(UNNAMED_SCOPE);
      unnamedElement.setAttribute(VALUE, unnamedScope);
      element.addContent(unnamedElement);
    }

    writeRules(element);
    return element;
  }

  private void writeRules(Element element) {
    for (DependencyRule rule : myRules) {
      Element ruleElement = writeRule(rule);
      if (ruleElement != null) {
        element.addContent(ruleElement);
      }
    }
  }

  @Nullable
  public NamedScope getScope(@Nullable final String name) {
    final NamedScope scope = super.getScope(name);
    if (scope == null) {
      final PackageSet packageSet = myUnnamedScopes.get(name);
      if (packageSet != null) {
        return new NamedScope.UnnamedScope(packageSet);
      }
    }
    //compatibility for predefined scopes: rename Project -> All
    if (scope == null && Comparing.strEqual(name, "Project")) {
      return super.getScope("All");
    }
    return scope;
  }

  @Nullable
  private static Element writeRule(DependencyRule rule) {
    NamedScope fromScope = rule.getFromScope();
    NamedScope toScope = rule.getToScope();
    if (fromScope == null || toScope == null) return null;
    Element ruleElement = new Element(DENY_RULE_KEY);
    ruleElement.setAttribute(FROM_SCOPE_KEY, fromScope.getName());
    ruleElement.setAttribute(TO_SCOPE_KEY, toScope.getName());
    ruleElement.setAttribute(IS_DENY_KEY, Boolean.valueOf(rule.isDenyRule()).toString());
    return ruleElement;
  }

  @Nullable
  private DependencyRule readRule(Element ruleElement) {
    String fromScope = ruleElement.getAttributeValue(FROM_SCOPE_KEY);
    String toScope = ruleElement.getAttributeValue(TO_SCOPE_KEY);
    String denyRule = ruleElement.getAttributeValue(IS_DENY_KEY);
    if (fromScope == null || toScope == null || denyRule == null) return null;
    final NamedScope fromNamedScope = getScope(fromScope);
    final NamedScope toNamedScope = getScope(toScope);
    if (fromNamedScope == null || toNamedScope == null) return null;
    return new DependencyRule(fromNamedScope, toNamedScope, Boolean.valueOf(denyRule).booleanValue());
  }

  public static class ScopesStateSplitter implements StateSplitter {
     public List<Pair<Element, String>> splitState(Element e) {
      final UniqueNameGenerator generator = new UniqueNameGenerator();
      final List<Pair<Element, String>> result = new ArrayList<Pair<Element, String>>();

      final Element[] elements = JDOMUtil.getElements(e);
      for (Element element : elements) {
        if (element.getName().equals("scope")) {
          element.detach();
          String scopeName = element.getAttributeValue("name");
          assert scopeName != null;
          final String name = generator.generateUniqueName(FileUtil.sanitizeFileName(scopeName)) + ".xml";
          result.add(new Pair<Element, String>(element, name));
        }
      }
       if (e.getChildren().size() > 0) {
         result.add(new Pair<Element, String>(e, generator.generateUniqueName("scope_settings") + ".xml"));
       }
       return result;
    }

    public void mergeStatesInto(Element target, Element[] elements) {
      for (Element element : elements) {
        if (element.getName().equals("scope")) {
          element.detach();
          target.addContent(element);
        }
        else {
          final Element[] states = JDOMUtil.getElements(element);
          for (Element state : states) {
            state.detach();
            target.addContent(state);
          }
          for (Object attr : element.getAttributes()) {
            target.setAttribute((Attribute)((Attribute)attr).clone());
          }
        }
      }
    }
  }

}

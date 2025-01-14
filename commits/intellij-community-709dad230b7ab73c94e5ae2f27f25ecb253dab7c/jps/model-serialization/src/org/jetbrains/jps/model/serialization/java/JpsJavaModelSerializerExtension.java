package org.jetbrains.jps.model.serialization.java;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsUrlList;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerConfigurationSerializer;
import org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerOptionsSerializer;
import org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerWorkspaceConfigurationSerializer;
import org.jetbrains.jps.model.serialization.library.JpsLibraryRootTypeSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class JpsJavaModelSerializerExtension extends JpsModelSerializerExtension {
  public static final String EXPORTED_ATTRIBUTE = "exported";
  public static final String SCOPE_ATTRIBUTE = "scope";
  public static final String OUTPUT_TAG = "output";
  public static final String URL_ATTRIBUTE = "url";
  public static final String LANGUAGE_LEVEL_ATTRIBUTE = "languageLevel";
  public static final String EXPLODED_TAG = "exploded";
  public static final String EXCLUDE_EXPLODED_TAG = "exclude-exploded";
  public static final String TEST_OUTPUT_TAG = "output-test";
  public static final String INHERIT_COMPILER_OUTPUT_ATTRIBUTE = "inherit-compiler-output";
  public static final String EXCLUDE_OUTPUT_TAG = "exclude-output";
  private static final String ANNOTATION_PATHS_TAG = "annotation-paths";
  private static final String JAVADOC_PATHS_TAG = "javadoc-paths";
  private static final String MODULE_LANGUAGE_LEVEL_ATTRIBUTE = "LANGUAGE_LEVEL";
  public static final String ROOT_TAG = "root";

  @Override
  public void loadRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
    loadExplodedDirectoryExtension(module, rootModel);
    loadJavaModuleExtension(module, rootModel);
  }

  @Override
  public void saveRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
    saveExplodedDirectoryExtension(module, rootModel);
    saveJavaModuleExtension(module, rootModel);
  }

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Arrays.asList(new JavaProjectExtensionSerializer(),
                         new JpsJavaCompilerConfigurationSerializer(),
                         new JpsJavaCompilerWorkspaceConfigurationSerializer(),
                         new JpsJavaCompilerOptionsSerializer("JavacSettings", "Javac"),
                         new JpsJavaCompilerOptionsSerializer("EclipseCompilerSettings", "Eclipse"));
  }

  @Override
  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element entry) {
    boolean exported = entry.getAttributeValue(EXPORTED_ATTRIBUTE) != null;
    String scopeName = entry.getAttributeValue(SCOPE_ATTRIBUTE);
    JpsJavaDependencyScope scope = scopeName != null ? JpsJavaDependencyScope.valueOf(scopeName) : JpsJavaDependencyScope.COMPILE;

    final JpsJavaDependencyExtension extension = getService().getOrCreateDependencyExtension(dependency);
    extension.setExported(exported);
    extension.setScope(scope);
  }

  @Override
  public void saveModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    JpsJavaDependencyExtension extension = getService().getDependencyExtension(dependency);
    if (extension != null) {
      if (extension.isExported()) {
        orderEntry.setAttribute(EXPORTED_ATTRIBUTE, "");
      }
      JpsJavaDependencyScope scope = extension.getScope();
      if (scope != JpsJavaDependencyScope.COMPILE) {
        orderEntry.setAttribute(SCOPE_ATTRIBUTE, scope.name());
      }
    }
  }

  @Override
  public List<JpsLibraryRootTypeSerializer> getLibraryRootTypeSerializers() {
    return Arrays.asList(new JpsLibraryRootTypeSerializer("JAVADOC", JpsOrderRootType.DOCUMENTATION, true),
                         new JpsLibraryRootTypeSerializer("ANNOTATIONS", JpsAnnotationRootType.INSTANCE, false));
  }

  @NotNull
  @Override
  public List<JpsLibraryRootTypeSerializer> getSdkRootTypeSerializers() {
    return Arrays.asList(new JpsLibraryRootTypeSerializer("javadocPath", JpsOrderRootType.DOCUMENTATION, true),
                         new JpsLibraryRootTypeSerializer("annotationsPath", JpsAnnotationRootType.INSTANCE, true));
  }

  @Override
  public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
    return Arrays.asList(new JpsModuleOutputPackagingElementSerializer(), new JpsTestModuleOutputPackagingElementSerializer());
  }

  private static void loadExplodedDirectoryExtension(JpsModule module, Element rootModelComponent) {
    final Element exploded = rootModelComponent.getChild(EXPLODED_TAG);
    if (exploded != null) {
      final ExplodedDirectoryModuleExtension extension = getService().getOrCreateExplodedDirectoryExtension(module);
      extension.setExcludeExploded(rootModelComponent.getChild(EXCLUDE_EXPLODED_TAG) != null);
      extension.setExplodedUrl(exploded.getAttributeValue(URL_ATTRIBUTE));
    }
  }

  private static void saveExplodedDirectoryExtension(JpsModule module, Element rootModelElement) {
    ExplodedDirectoryModuleExtension extension = getService().getExplodedDirectoryExtension(module);
    if (extension != null) {
      if (extension.isExcludeExploded()) {
        rootModelElement.addContent(0, new Element(EXCLUDE_EXPLODED_TAG));
      }
      rootModelElement.addContent(0, new Element(EXPLODED_TAG).setAttribute(URL_ATTRIBUTE, extension.getExplodedUrl()));
    }
  }

  private static void loadJavaModuleExtension(JpsModule module, Element rootModelComponent) {
    final JpsJavaModuleExtension extension = getService().getOrCreateModuleExtension(module);
    final Element outputTag = rootModelComponent.getChild(OUTPUT_TAG);
    String outputUrl = outputTag != null ? outputTag.getAttributeValue(URL_ATTRIBUTE) : null;
    extension.setOutputUrl(outputUrl);
    final Element testOutputTag = rootModelComponent.getChild(TEST_OUTPUT_TAG);
    String testOutputUrl = testOutputTag != null ? testOutputTag.getAttributeValue(URL_ATTRIBUTE) : null;
    extension.setTestOutputUrl(StringUtil.isEmpty(testOutputUrl) ? outputUrl : testOutputUrl);

    extension.setInheritOutput(Boolean.parseBoolean(rootModelComponent.getAttributeValue(INHERIT_COMPILER_OUTPUT_ATTRIBUTE)));
    extension.setExcludeOutput(rootModelComponent.getChild(EXCLUDE_OUTPUT_TAG) != null);

    final String languageLevel = rootModelComponent.getAttributeValue(MODULE_LANGUAGE_LEVEL_ATTRIBUTE);
    if (languageLevel != null) {
      extension.setLanguageLevel(LanguageLevel.valueOf(languageLevel));
    }

    loadAdditionalRoots(rootModelComponent, ANNOTATION_PATHS_TAG, extension.getAnnotationRoots());
    loadAdditionalRoots(rootModelComponent, JAVADOC_PATHS_TAG, extension.getJavadocRoots());
  }

  private static void saveJavaModuleExtension(JpsModule module, Element rootModelComponent) {
    JpsJavaModuleExtension extension = getService().getModuleExtension(module);
    if (extension == null) return;
    if (extension.isExcludeOutput()) {
      rootModelComponent.addContent(0, new Element(EXCLUDE_OUTPUT_TAG));
    }

    String testOutputUrl = extension.getTestOutputUrl();
    if (testOutputUrl != null) {
      rootModelComponent.addContent(0, new Element(TEST_OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, testOutputUrl));
    }

    String outputUrl = extension.getOutputUrl();
    if (outputUrl != null) {
      rootModelComponent.addContent(0, new Element(OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, outputUrl));
    }

    LanguageLevel languageLevel = extension.getLanguageLevel();
    if (languageLevel != null) {
      rootModelComponent.setAttribute(MODULE_LANGUAGE_LEVEL_ATTRIBUTE, languageLevel.name());
    }
    rootModelComponent.setAttribute(INHERIT_COMPILER_OUTPUT_ATTRIBUTE, String.valueOf(extension.isInheritOutput()));
    saveAdditionalRoots(rootModelComponent, JAVADOC_PATHS_TAG, extension.getJavadocRoots());
    saveAdditionalRoots(rootModelComponent, ANNOTATION_PATHS_TAG, extension.getAnnotationRoots());
  }

  private static void loadAdditionalRoots(Element rootModelComponent, final String rootsTagName, final JpsUrlList result) {
    final Element roots = rootModelComponent.getChild(rootsTagName);
    for (Element root : JDOMUtil.getChildren(roots, ROOT_TAG)) {
      result.addUrl(root.getAttributeValue(URL_ATTRIBUTE));
    }
  }

  private static void saveAdditionalRoots(Element rootModelComponent, final String rootsTagName, final JpsUrlList list) {
    List<String> urls = list.getUrls();
    if (!urls.isEmpty()) {
      Element roots = new Element(rootsTagName);
      for (String url : urls) {
        roots.addContent(new Element(ROOT_TAG).setAttribute(URL_ATTRIBUTE, url));
      }
      rootModelComponent.addContent(roots);
    }
  }

  private static JpsJavaExtensionService getService() {
    return JpsJavaExtensionService.getInstance();
  }

  private static class JpsModuleOutputPackagingElementSerializer
    extends JpsPackagingElementSerializer<JpsProductionModuleOutputPackagingElement> {
    private JpsModuleOutputPackagingElementSerializer() {
      super("module-output", JpsProductionModuleOutputPackagingElement.class);
    }

    @Override
    public JpsProductionModuleOutputPackagingElement load(Element element) {
      JpsModuleReference reference = JpsElementFactory.getInstance().createModuleReference(element.getAttributeValue("name"));
      return getService().createProductionModuleOutput(reference);
    }

    @Override
    public void save(JpsProductionModuleOutputPackagingElement element, Element tag) {
      tag.setAttribute("name", element.getModuleReference().getModuleName());
    }
  }

  private static class JpsTestModuleOutputPackagingElementSerializer extends JpsPackagingElementSerializer<JpsTestModuleOutputPackagingElement> {
    private JpsTestModuleOutputPackagingElementSerializer() {
      super("module-test-output", JpsTestModuleOutputPackagingElement.class);
    }

    @Override
    public JpsTestModuleOutputPackagingElement load(Element element) {
      JpsModuleReference reference = JpsElementFactory.getInstance().createModuleReference(element.getAttributeValue("name"));
      return getService().createTestModuleOutput(reference);
    }

    @Override
    public void save(JpsTestModuleOutputPackagingElement element, Element tag) {
      tag.setAttribute("name", element.getModuleReference().getModuleName());
    }
  }

  private static class JavaProjectExtensionSerializer extends JpsProjectExtensionSerializer {
    public JavaProjectExtensionSerializer() {
      super(null, "ProjectRootManager");
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      JpsJavaProjectExtension extension = getService().getOrCreateProjectExtension(project);
      final Element output = componentTag.getChild(OUTPUT_TAG);
      if (output != null) {
        String url = output.getAttributeValue(URL_ATTRIBUTE);
        if (url != null) {
          extension.setOutputUrl(url);
        }
      }
      String languageLevel = componentTag.getAttributeValue(LANGUAGE_LEVEL_ATTRIBUTE);
      if (languageLevel != null) {
        extension.setLanguageLevel(LanguageLevel.valueOf(languageLevel));
      }
    }

    @Override
    public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      JpsJavaProjectExtension extension = getService().getProjectExtension(project);
      if (extension == null) return;

      String outputUrl = extension.getOutputUrl();
      if (outputUrl != null) {
        componentTag.addContent(new Element(OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, outputUrl));
      }
      LanguageLevel level = extension.getLanguageLevel();
      componentTag.setAttribute(LANGUAGE_LEVEL_ATTRIBUTE, level.name());
      componentTag.setAttribute("assert-keyword", Boolean.toString(level.compareTo(LanguageLevel.JDK_1_4) >= 0));
      componentTag.setAttribute("jdk-15", Boolean.toString(level.compareTo(LanguageLevel.JDK_1_5) >= 0));
    }
  }
}

package com.intellij.uiDesigner.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.inspections.EditorQuickFixProvider;
import com.intellij.uiDesigner.inspections.FormErrorCollector;
import com.intellij.uiDesigner.inspections.StringDescriptorInspection;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.BorderProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadTabbedPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class I18nFormInspection extends StringDescriptorInspection {
  public I18nFormInspection() {
    super("HardCodedStringLiteral");
  }

  protected void checkStringDescriptor(final Module module,
                                       final IComponent component,
                                       final IProperty prop,
                                       final StringDescriptor descriptor,
                                       final FormErrorCollector collector) {
    if (isHardCodedStringDescriptor(descriptor)) {
      if (isPropertyDescriptor(prop)) {
        if (isSetterNonNls(module.getProject(),
                           GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module),
                           component.getComponentClassName(), prop.getName())) {
          return;
        }
      }

      EditorQuickFixProvider provider;

      if (prop.getName().equals(BorderProperty.NAME)) {
        provider = new EditorQuickFixProvider() {
          public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
            return new I18nizeFormBorderQuickFix(editor, UIDesignerBundle.message("i18n.quickfix.border.title"),
                                                 (RadContainer)component);
          }
        };
      }
      else if (prop.getName().equals(RadTabbedPane.TAB_TITLE_PROPERTY) || prop.getName().equals(RadTabbedPane.TAB_TOOLTIP_PROPERTY)) {
        provider = new EditorQuickFixProvider() {
          public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
            return new I18nizeTabTitleQuickFix(editor, UIDesignerBundle.message("i18n.quickfix.tab.title", prop.getName()),
                                               component, prop.getName());
          }
        };
      }
      else {
        provider = new EditorQuickFixProvider() {
          public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
            return new I18nizeFormPropertyQuickFix(editor, UIDesignerBundle.message("i18n.quickfix.property", prop.getName()),
                                                   component,
                                                   (IntrospectedProperty)prop);
          }
        };
      }

      collector.addError(getID(), component, prop,
                         UIDesignerBundle.message("inspection.i18n.message.in.form", descriptor.getValue()),
                         provider);
    }
  }

  private static boolean isPropertyDescriptor(final IProperty prop) {
    return !prop.getName().equals(BorderProperty.NAME) && !prop.getName().equals(RadTabbedPane.TAB_TITLE_PROPERTY) &&
           !prop.getName().equals(RadTabbedPane.TAB_TOOLTIP_PROPERTY);
  }

  private static boolean isHardCodedStringDescriptor(final StringDescriptor descriptor) {
    if (descriptor.isNoI18n()) {
      return false;
    }
    return descriptor.getBundleName() == null &&
           descriptor.getKey() == null &&
           StringUtil.containsAlphaCharacters(descriptor.getValue());
  }

  private static boolean isSetterNonNls(final Project project, final GlobalSearchScope searchScope,
                                        final String componentClassName, final String propertyName) {
    PsiClass componentClass = JavaPsiFacade.getInstance(project).findClass(componentClassName, searchScope);
    if (componentClass == null) {
      return false;
    }
    PsiMethod setter = PropertyUtil.findPropertySetter(componentClass, propertyName, false, true);
    if (setter != null) {
      PsiParameter[] parameters = setter.getParameterList().getParameters();
      if (parameters.length == 1 &&
          "java.lang.String".equals(parameters[0].getType().getCanonicalText()) &&
          AnnotationUtil.isAnnotated(parameters [0], AnnotationUtil.NON_NLS, false)) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      final PsiDirectory directory = file.getContainingDirectory();
      if (directory != null && I18nInspection.isPackageNonNls(JavaDirectoryService.getInstance().getPackage(directory))) {
        return null;
      }
    }

    return super.checkFile(file, manager, isOnTheFly);
  }
}

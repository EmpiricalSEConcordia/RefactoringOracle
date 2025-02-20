package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EventDispatcher;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 29, 2003
 */
public class ModuleTypeStep extends ModuleWizardStep {
  private JPanel myPanel;
  private JRadioButton myRbCreateNewModule;
  private JRadioButton myRbImportModule;
  private JRadioButton myRbImportForeignModule;
  private JComboBox myCbForeignModuleType;
  private FieldPanel myModulePathFieldPanel;
  private JList myTypesList;
  private JEditorPane myModuleDescriptionPane;

  private ModuleType myModuleType = ModuleType.JAVA;
  private Runnable myDoubleClickAction = null;

  final EventDispatcher<UpdateListener> myEventDispatcher = EventDispatcher.create(UpdateListener.class);
  private final ButtonGroup myButtonGroup;

  public static interface UpdateListener extends EventListener{
    void moduleTypeSelected(ModuleType type);
    void importModuleOptionSelected(boolean selected);
  }

  public ModuleTypeStep(boolean createNewProject) {
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    myModuleDescriptionPane = new JEditorPane();
    myModuleDescriptionPane.setContentType("text/html");
    myModuleDescriptionPane.setEditable(false);

    final ModuleType[] allModuleTypes = ModuleTypeManager.getInstance().getRegisteredTypes();

    myTypesList = new JList(allModuleTypes);
    myTypesList.setSelectionModel(new PermanentSingleSelectionModel());
    myTypesList.setCellRenderer(new ModuleTypesListCellRenderer());
    myTypesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        final ModuleType typeSelected = (ModuleType)myTypesList.getSelectedValue();
        myModuleType = typeSelected;
        myModuleDescriptionPane.setText("<html><body><font face=\"verdana\" size=\"-1\">"+typeSelected.getDescription()+"</font></body></html>");
        myEventDispatcher.getMulticaster().moduleTypeSelected(typeSelected);
      }
    });
    myTypesList.setSelectedIndex(0);
    myTypesList.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2){
            if (myDoubleClickAction != null) {
              if (myTypesList.getSelectedValue() != null) {
                myDoubleClickAction.run();
              }
            }
          }
        }
      }
    );

    myRbCreateNewModule = new JRadioButton("Create new module", true);
    myRbCreateNewModule.setMnemonic('C');
    myRbImportModule = new JRadioButton("Import existing module");
    myRbImportModule.setMnemonic('I');
    myRbImportForeignModule = new JRadioButton("Import module from");
    myRbImportForeignModule.setMnemonic('m');
    myCbForeignModuleType = new JComboBox(new String[]{"Eclipse project"});
    myCbForeignModuleType.setEnabled(false);
    myButtonGroup = new ButtonGroup();
    myButtonGroup.add(myRbCreateNewModule);
    myButtonGroup.add(myRbImportModule);
    myButtonGroup.add(myRbImportForeignModule);
    ModulesRbListener listener = new ModulesRbListener();
    myRbCreateNewModule.addItemListener(listener);
    myRbImportForeignModule.addItemListener(listener);
    myRbImportModule.addItemListener(listener);

    JTextField tfModuleFilePath = new JTextField();
    myModulePathFieldPanel = createFieldPanel(tfModuleFilePath, "Path to IDEA module file (.iml):", new BrowseFilesListener(tfModuleFilePath, "Select IDEA module file (.iml) to import", null, new ModuleFileChooserDescriptor()));
    myModulePathFieldPanel.setEnabled(false);

    if (createNewProject) {
      final JLabel moduleTypeLabel = new JLabel("Select module type:");
      moduleTypeLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
      myPanel.add(moduleTypeLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 0, 10), 0, 0));
    }
    else {
      myPanel.add(myRbCreateNewModule, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 8, 10), 0, 0));
    }
    final JLabel descriptionLabel = new JLabel("Description:");
    descriptionLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
    myPanel.add(descriptionLabel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    final JScrollPane typesListScrollPane = ScrollPaneFactory.createScrollPane(myTypesList);
    final Dimension preferredSize = calcTypeListPreferredSize(allModuleTypes);
    typesListScrollPane.setPreferredSize(preferredSize);
    typesListScrollPane.setMinimumSize(preferredSize);
    myPanel.add(typesListScrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.2, (createNewProject? 1.0 : 0.0), GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, createNewProject? 10 : 30, 0, 10), 0, 0));

    final JScrollPane descriptionScrollPane = ScrollPaneFactory.createScrollPane(myModuleDescriptionPane);
    descriptionScrollPane.setPreferredSize(new Dimension(preferredSize.width * 3, preferredSize.height));
    myPanel.add(descriptionScrollPane, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.8, (createNewProject? 1.0 : 0.0), GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 10), 0, 0));

    if (!createNewProject) {
      myPanel.add(myRbImportModule, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(16, 10, 0, 10), 0, 0));
      myPanel.add(myModulePathFieldPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 30, 0, 10), 0, 0));

      myPanel.add(myRbImportForeignModule, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(16, 10, 0, 10), 0, 0));
      myPanel.add(myCbForeignModuleType, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 30, 8, 10), 0, 0));
    }
  }

  private Dimension calcTypeListPreferredSize(final ModuleType[] allModuleTypes) {
    int width = 0;
    int height = 0;
    final FontMetrics fontMetrics = myTypesList.getFontMetrics(myTypesList.getFont());
    final int fontHeight = fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent();
    for (int idx = 0; idx < allModuleTypes.length; idx++) {
      final ModuleType type = allModuleTypes[idx];
      final Icon icon = type.getBigIcon();
      final int iconHeight = icon != null? icon.getIconHeight(): 0;
      final int iconWidth = icon != null? icon.getIconWidth(): 0;
      height += Math.max(iconHeight, fontHeight) + 6;
      width = Math.max(width, iconWidth + fontMetrics.stringWidth(type.getName()) + 10);
    }
    return new Dimension(width, height);
  }

  public String getHelpId() {
    return "project.creatingModules.page1";
  }

  public void setModuleListDoubleClickAction(Runnable runnable) {
    myDoubleClickAction = runnable;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public Icon getIcon() {
    return ICON;
  }

  public boolean validate() {
    if (myRbImportModule.isSelected()) {
      final String path = myModulePathFieldPanel.getText().trim();
      if (path.length() == 0) {
        Messages.showErrorDialog("Please specify path to IDEA module file (.iml)", "Module File Path Not Specified");
        myModulePathFieldPanel.getTextField().requestFocus();
        return false;
      }
      final File file = new File(path);
      if (!file.exists()) {
        Messages.showErrorDialog("The specified path to module file does not exist", "Module File Does Not Exist");
        myModulePathFieldPanel.getTextField().requestFocus();
        return false;
      }
      if (!StdFileTypes.IDEA_MODULE.equals(FileTypeManager.getInstance().getFileTypeByFileName(file.getName()))) {
        Messages.showErrorDialog("The \"" + path + "\"\nis not an IDEA module file (.iml)", "Incorrect File Type");
        myModulePathFieldPanel.getTextField().requestFocus();
        return false;
      }
    }
    return true;
  }

  public boolean isNextButtonEnabled() {
    return !myRbImportModule.isSelected();
  }
  public boolean isCreateNewModule() {
    return myRbCreateNewModule.isSelected();
  }
  public boolean isImportExistingModule() {
    return myRbImportModule.isSelected();
  }
  public boolean isImportForeignModule() {
    return myRbImportForeignModule.isSelected();
  }

  public String getModuleFilePath() {
    return myModulePathFieldPanel.getText().trim().replace(File.separatorChar, '/');
  }

  public ModuleType getModuleType() {
    return myModuleType;
  }

  public void addUpdateListener(UpdateListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeUpdateListener(UpdateListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public void updateDataModel() {
  }

  public JComponent getPreferredFocusedComponent() {
    return myTypesList;
  }

  private class ModulesRbListener implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      final JComponent toFocus;
      ButtonModel selection = myButtonGroup.getSelection();
      setControlsEnabled(selection);
      if (selection == myRbCreateNewModule.getModel()) {
        toFocus = myTypesList;
        myEventDispatcher.getMulticaster().importModuleOptionSelected(false);
      }
      else if (selection == myRbImportModule.getModel()) { // import existing
        toFocus = myModulePathFieldPanel.getTextField();
        myEventDispatcher.getMulticaster().importModuleOptionSelected(true);
      }
      else if (selection == myRbImportForeignModule.getModel()) {
        myEventDispatcher.getMulticaster().importModuleOptionSelected(false);
        toFocus = myCbForeignModuleType;
      }
      else {
        toFocus = null;
      }

      if (toFocus != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            toFocus.requestFocus();
          }
        });
      }
    }
  }

  private void setControlsEnabled(ButtonModel selection) {
    boolean newModuleEnabled = selection == myRbCreateNewModule.getModel();
    myTypesList.setEnabled(newModuleEnabled);
    myModuleDescriptionPane.setEnabled(newModuleEnabled);

    boolean importModuleEnabled = selection == myRbImportModule.getModel();
    myModulePathFieldPanel.setEnabled(importModuleEnabled);

    boolean importForeignModuleEnabled = selection == myRbImportForeignModule.getModel();
    myCbForeignModuleType.setEnabled(importForeignModuleEnabled);
  }

  private static class ModuleFileChooserDescriptor extends FileChooserDescriptor {
    public ModuleFileChooserDescriptor() {
      super(true, false, false, false, false, false);
      setHideIgnored(false);
    }

    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      final boolean isVisible = super.isFileVisible(file, showHiddenFiles);
      if (!isVisible || file.isDirectory()) {
        return isVisible;
      }
      return StdFileTypes.IDEA_MODULE.equals(FileTypeManager.getInstance().getFileTypeByFile(file));
    }
  }

  private static class ModuleTypesListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      final JLabel rendererComponent = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      final ModuleType moduleType = (ModuleType)value;
      rendererComponent.setIcon(moduleType.getBigIcon());
      rendererComponent.setText(moduleType.getName());
      return rendererComponent;
    }
  }

  private static class PermanentSingleSelectionModel extends DefaultListSelectionModel {
    public PermanentSingleSelectionModel() {
      super.setSelectionMode(SINGLE_SELECTION);
    }

    public final void setSelectionMode(int selectionMode) {
    }

    public final void removeSelectionInterval(int index0, int index1) {
    }
  }

}

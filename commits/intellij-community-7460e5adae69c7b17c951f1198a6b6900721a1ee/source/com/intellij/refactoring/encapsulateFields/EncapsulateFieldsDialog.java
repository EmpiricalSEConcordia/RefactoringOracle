package com.intellij.refactoring.encapsulateFields;

import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RowIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

public class EncapsulateFieldsDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance(
          "#com.intellij.refactoring.encapsulateFields.EncapsulateFieldsDialog"
  );

  public static interface Callback {
    void run(EncapsulateFieldsDialog dialog);
  }

  private static final int CHECKED_COLUMN = 0;
  private static final int FIELD_COLUMN = 1;
  private static final int GETTER_COLUMN = 2;
  private static final int SETTER_COLUMN = 3;

  private final Project myProject;
  private final PsiClass myClass;
  private final Callback myCallback;

  private PsiField[] myFields;
  private boolean[] myCheckedMarks;
  private boolean[] myFinalMarks;
  private String[] myFieldNames;
  private String[] myGetterNames;
  private PsiMethod[] myGetterPrototypes;
  private String[] mySetterNames;
  private PsiMethod[] mySetterPrototypes;

  private JTable myTable;
  private MyTableModel myTableModel;

  private JCheckBox myCbEncapsulateGet = new JCheckBox("Get access");
  private JCheckBox myCbEncapsulateSet = new JCheckBox("Set access");
  private JCheckBox myCbUseAccessorsWhenAccessible = new JCheckBox("Use accessors even when field is accessible");
  private JRadioButton myRbFieldPrivate = new JRadioButton("Private");
  private JRadioButton myRbFieldProtected = new JRadioButton("Protected");
  private JRadioButton myRbFieldPackageLocal = new JRadioButton("Package local");
  private JRadioButton myRbFieldAsIs = new JRadioButton("As is");
  private JRadioButton myRbAccessorPublic = new JRadioButton("Public");
  private JRadioButton myRbAccessorProtected = new JRadioButton("Protected");
  private JRadioButton myRbAccessorPrivate = new JRadioButton("Private");
  private JRadioButton myRbAccessorPackageLocal = new JRadioButton("Package local");

  {
    myCbEncapsulateGet.setFocusable(false);
    myCbEncapsulateSet.setFocusable(false);
    myCbUseAccessorsWhenAccessible.setFocusable(false);

    myRbAccessorPackageLocal.setFocusable(false);
    myRbAccessorPrivate.setFocusable(false);
    myRbAccessorProtected.setFocusable(false);
    myRbAccessorPublic.setFocusable(false);

    myRbFieldAsIs.setFocusable(false);
    myRbFieldPackageLocal.setFocusable(false);
    myRbFieldPrivate.setFocusable(false);
    myRbFieldProtected.setFocusable(false);
  }

  public EncapsulateFieldsDialog(Project project, PsiClass aClass, final Set preselectedFields, Callback callback) {
    super(project, true);
    myProject = project;
    myClass = aClass;
    myCallback = callback;

    String title = "Encapsulate Fields";
    String qName = myClass.getQualifiedName();
    if (qName != null) {
      title += " - " + qName;
    }
    setTitle(title);

    myFields = myClass.getFields();
    myFieldNames = new String[myFields.length];
    myCheckedMarks = new boolean[myFields.length];
    myFinalMarks = new boolean[myFields.length];
    myGetterNames = new String[myFields.length];
    mySetterNames = new String[myFields.length];
    myGetterPrototypes = new PsiMethod[myFields.length];
    mySetterPrototypes = new PsiMethod[myFields.length];
    for (int idx = 0; idx < myFields.length; idx++) {
      PsiField field = myFields[idx];
      myCheckedMarks[idx] = preselectedFields.contains(field);
      myFinalMarks[idx] = field.hasModifierProperty(PsiModifier.FINAL);
      myFieldNames[idx] =
              PsiFormatUtil.formatVariable(field,
                      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER,
                      PsiSubstitutor.EMPTY
              );
      myGetterNames[idx] = PropertyUtil.suggestGetterName(myProject, field);
      mySetterNames[idx] = PropertyUtil.suggestSetterName(myProject, field);
      myGetterPrototypes[idx] = generateMethodPrototype(field, myGetterNames[idx], true);
      mySetterPrototypes[idx] = generateMethodPrototype(field, mySetterNames[idx], false);
    }

    init();
  }

  public PsiField[] getSelectedFields() {
    int[] rows = getCheckedRows();
    PsiField[] selectedFields = new PsiField[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      selectedFields[idx] = myFields[rows[idx]];
    }
    return selectedFields;
  }

  public String[] getGetterNames() {
    int[] rows = getCheckedRows();
    String[] selectedGetters = new String[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      selectedGetters[idx] = myGetterNames[rows[idx]];
    }
    return selectedGetters;
  }

  public String[] getSetterNames() {
    int[] rows = getCheckedRows();
    String[] selectedSetters = new String[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      selectedSetters[idx] = mySetterNames[rows[idx]];
    }
    return selectedSetters;
  }

  public PsiMethod[] getGetterPrototypes() {
    if (isToEncapsulateGet()) {
      int[] rows = getCheckedRows();
      PsiMethod[] selectedGetters = new PsiMethod[rows.length];
      for (int idx = 0; idx < rows.length; idx++) {
        selectedGetters[idx] = myGetterPrototypes[rows[idx]];
      }
      return selectedGetters;
    } else {
      return null;
    }
  }

  public PsiMethod[] getSetterPrototypes() {
    if (isToEncapsulateSet()) {
      int[] rows = getCheckedRows();
      PsiMethod[] selectedSetters = new PsiMethod[rows.length];
      for (int idx = 0; idx < rows.length; idx++) {
        selectedSetters[idx] = mySetterPrototypes[rows[idx]];
      }
      return selectedSetters;
    } else {
      return null;
    }
  }

  public boolean isToEncapsulateGet() {
    return myCbEncapsulateGet.isSelected();
  }

  public boolean isToEncapsulateSet() {
    return myCbEncapsulateSet.isSelected();
  }

  public boolean isToUseAccessorsWhenAccessible() {
    if (getFieldsVisibility() == null) {
      // "as is"
      return true;
    }
    return myCbUseAccessorsWhenAccessible.isSelected();
  }

  public String getFieldsVisibility() {
    if (myRbFieldPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    } else if (myRbFieldPackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    } else if (myRbFieldProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    } else if (myRbFieldAsIs.isSelected()) {
      return null;
    } else {
      LOG.assertTrue(false);
      return null;
    }
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.encapsulateFields.EncalpsulateFieldsDialog";
  }

  public String getAccessorsVisibility() {
    if (myRbAccessorPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    } else if (myRbAccessorProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    } else if (myRbAccessorPackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    } else if (myRbAccessorPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    } else {
      LOG.assertTrue(false);
      return null;
    }
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createTable(), BorderLayout.CENTER);

    myCbEncapsulateGet.setMnemonic('G');
    myCbEncapsulateSet.setMnemonic('S');
    myCbUseAccessorsWhenAccessible.setMnemonic('U');
    myRbFieldPrivate.setMnemonic('i');
    myRbFieldProtected.setMnemonic('r');
    myRbFieldPackageLocal.setMnemonic('c');
    myRbFieldAsIs.setMnemonic('A');
    myRbAccessorPublic.setMnemonic('b');
    myRbAccessorProtected.setMnemonic('o');
    myRbAccessorPrivate.setMnemonic('v');
    myRbAccessorPackageLocal.setMnemonic('k');

    ButtonGroup fieldGroup = new ButtonGroup();
    fieldGroup.add(myRbFieldAsIs);
    fieldGroup.add(myRbFieldPackageLocal);
    fieldGroup.add(myRbFieldPrivate);
    fieldGroup.add(myRbFieldProtected);

    ButtonGroup methodGroup = new ButtonGroup();
    methodGroup.add(myRbAccessorPackageLocal);
    methodGroup.add(myRbAccessorPrivate);
    methodGroup.add(myRbAccessorProtected);
    methodGroup.add(myRbAccessorPublic);

    myCbEncapsulateGet.setSelected(true);
    myCbEncapsulateSet.setSelected(true);
    ActionListener checkboxListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myCbEncapsulateGet.equals(e.getSource())) {
          if (!myCbEncapsulateGet.isSelected()) {
            myCbEncapsulateSet.setSelected(true);
          }
        } else {
          // myCbEncapsulateSet is the source
          if (!myCbEncapsulateSet.isSelected()) {
            myCbEncapsulateGet.setSelected(true);
          }
        }
        int[] rows = myTable.getSelectedRows();
        myTableModel.fireTableDataChanged();
        TableUtil.selectRows(myTable, rows);
      }
    };
    myCbEncapsulateGet.addActionListener(checkboxListener);
    myCbEncapsulateSet.addActionListener(checkboxListener);
    myRbFieldAsIs.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        myCbUseAccessorsWhenAccessible.setEnabled(!myRbFieldAsIs.isSelected());
      }
    }
    );
    myCbUseAccessorsWhenAccessible.setSelected(
            RefactoringSettings.getInstance().ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE
    );

    myRbFieldPrivate.setSelected(true);
    myRbAccessorPublic.setSelected(true);

    Box leftBox = Box.createVerticalBox();
    myCbEncapsulateGet.setPreferredSize(myCbUseAccessorsWhenAccessible.getPreferredSize());
    leftBox.add(myCbEncapsulateGet);
    leftBox.add(myCbEncapsulateSet);
    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.setBorder(IdeBorderFactory.createTitledBorder("Encapsulate"));
    leftPanel.add(leftBox, BorderLayout.CENTER);
    leftPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    Box rightBox = Box.createVerticalBox();
    rightBox.add(myCbUseAccessorsWhenAccessible);
    JPanel rightPanel = new JPanel(new BorderLayout());
    rightPanel.setBorder(IdeBorderFactory.createTitledBorder("Options"));
    rightPanel.add(rightBox, BorderLayout.CENTER);
    rightPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    Box encapsulateBox = Box.createHorizontalBox();
    encapsulateBox.add(leftPanel);
    encapsulateBox.add(Box.createHorizontalStrut(5));
    encapsulateBox.add(rightPanel);

    Box fieldsBox = Box.createVerticalBox();
    fieldsBox.add(myRbFieldPrivate);
    fieldsBox.add(myRbFieldPackageLocal);
    fieldsBox.add(myRbFieldProtected);
    fieldsBox.add(myRbFieldAsIs);
    JPanel fieldsVisibilityPanel = new JPanel(new BorderLayout());
    fieldsVisibilityPanel.setBorder(IdeBorderFactory.createTitledBorder("Encapsulated Fields' Visibility"));
    fieldsVisibilityPanel.add(fieldsBox, BorderLayout.CENTER);
    fieldsVisibilityPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    Box methodsBox = Box.createVerticalBox();
    methodsBox.add(myRbAccessorPublic);
    methodsBox.add(myRbAccessorProtected);
    methodsBox.add(myRbAccessorPackageLocal);
    methodsBox.add(myRbAccessorPrivate);
    JPanel methodsVisibilityPanel = new JPanel(new BorderLayout());
    methodsVisibilityPanel.setBorder(IdeBorderFactory.createTitledBorder("Accessors' Visibility"));
    methodsVisibilityPanel.add(methodsBox, BorderLayout.CENTER);
    methodsVisibilityPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    Box visibilityBox = Box.createHorizontalBox();
    visibilityBox.add(fieldsVisibilityPanel);
    visibilityBox.add(Box.createHorizontalStrut(5));
    visibilityBox.add(methodsVisibilityPanel);

    Box box = Box.createVerticalBox();
    box.add(encapsulateBox);
    box.add(Box.createVerticalStrut(5));
    box.add(visibilityBox);

    JPanel boxPanel = new JPanel(new BorderLayout());
    boxPanel.add(box, BorderLayout.CENTER);
    boxPanel.add(Box.createVerticalStrut(5), BorderLayout.NORTH);
    panel.add(boxPanel, BorderLayout.SOUTH);

    return panel;
  }

  private JComponent createTable() {
    myTableModel = new MyTableModel();
    myTable = new Table(myTableModel);
    myTable.setSurrendersFocusOnKeystroke(true);
    MyTableRenderer renderer = new MyTableRenderer();
    TableColumnModel columnModel = myTable.getColumnModel();
    columnModel.getColumn(FIELD_COLUMN).setCellRenderer(renderer);
    columnModel.getColumn(GETTER_COLUMN).setCellRenderer(renderer);
    columnModel.getColumn(SETTER_COLUMN).setCellRenderer(renderer);
    columnModel.getColumn(CHECKED_COLUMN).setMaxWidth(new JCheckBox().getPreferredSize().width);

    myTable.setPreferredScrollableViewportSize(new Dimension(550, myTable.getRowHeight() * 12));
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
//    JLabel label = new JLabel("Fields to Encapsulate");
//    CompTitledBorder titledBorder = new CompTitledBorder(label);
    Border titledBorder = IdeBorderFactory.createTitledBorder("Fields to Encapsulate");
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    scrollPane.setBorder(border);
    // make ESC and ENTER work when focus is in the table
    myTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null) {
          editor.cancelCellEditing();
        } else {
          doCancelAction();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );
    // make SPACE check/uncheck selected rows
    InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    myTable.getActionMap().put("enable_disable", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] rows = myTable.getSelectedRows();
        if (rows.length > 0) {
          boolean valueToBeSet = false;
          for (int idx = 0; idx < rows.length; idx++) {
            if (!myCheckedMarks[rows[idx]]) {
              valueToBeSet = true;
              break;
            }
          }
          for (int idx = 0; idx < rows.length; idx++) {
            myCheckedMarks[rows[idx]] = valueToBeSet;
          }
          myTableModel.fireTableRowsUpdated(rows[0], rows[rows.length - 1]);
          TableUtil.selectRows(myTable, rows);
        }
      }
    }
    );
    // make ENTER work when the table has focus
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "invokeImpl");
    myTable.getActionMap().put("invokeImpl", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        } else {
          clickDefaultButton();
        }
      }
    }
    );
    return scrollPane;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  protected void doAction() {
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    String errorString = validateData();
    if (errorString == null) {
      myCallback.run(this);
      RefactoringSettings settings = RefactoringSettings.getInstance();
      settings.ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE = myCbUseAccessorsWhenAccessible.isSelected();
    } else { // were errors
      RefactoringMessageUtil.showErrorMessage("Encapsulate Fields", errorString, HelpID.ENCAPSULATE_FIELDS, myProject);
    }
  }

  /**
   * @return error string if errors were found, or null if everything is ok
   */
  private String validateData() {
    PsiManager manager = PsiManager.getInstance(myProject);
    for (int idx = 0; idx < myFields.length; idx++) {
      if (myCheckedMarks[idx]) {
        String name;
        if (isToEncapsulateGet()) {
          name = myGetterNames[idx];
          if (!manager.getNameHelper().isIdentifier(name)) {
            return RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
          }
        }
        if (!myFinalMarks[idx] && isToEncapsulateSet()) {
          name = mySetterNames[idx];
          if (!manager.getNameHelper().isIdentifier(name)) {
            return RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
          }
        }
      }
    }
    return null;
  }

  private PsiMethod generateMethodPrototype(PsiField field, String methodName, boolean isGetter) {
    PsiMethod prototype = isGetter
                          ? PropertyUtil.generateGetterPrototype(field)
                          : PropertyUtil.generateSetterPrototype(field);
    try {
      PsiElementFactory factory = field.getManager().getElementFactory();
      PsiIdentifier identifier = factory.createIdentifier(methodName);
      prototype.getNameIdentifier().replace(identifier);
      //prototype.getModifierList().setModifierProperty(getAccessorsVisibility(), true);
      return prototype;
    } catch (IncorrectOperationException e) {
      return null;
    }
  }

  private int[] getCheckedRows() {
    int count = 0;
    for (int idx = 0; idx < myCheckedMarks.length; idx++) {
      if (myCheckedMarks[idx]) {
        count++;
      }
    }
    int[] rows = new int[count];
    int currentRow = 0;
    for (int idx = 0; idx < myCheckedMarks.length; idx++) {
      if (myCheckedMarks[idx]) {
        rows[currentRow++] = idx;
      }
    }
    return rows;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.ENCAPSULATE_FIELDS);
  }

  private class MyTableModel extends AbstractTableModel {
    public int getColumnCount() {
      return 4;
    }

    public int getRowCount() {
      return myFields.length;
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKED_COLUMN:
          return myCheckedMarks[rowIndex] ? Boolean.TRUE : Boolean.FALSE;
        case FIELD_COLUMN:
          return myFieldNames[rowIndex];
        case GETTER_COLUMN:
          return myGetterNames[rowIndex];
        case SETTER_COLUMN:
          return mySetterNames[rowIndex];
        default:
          throw new RuntimeException("Incorrect column index");
      }
    }

    public String getColumnName(int column) {
      switch (column) {
        case CHECKED_COLUMN:
          return " ";
        case FIELD_COLUMN:
          return "Field";
        case GETTER_COLUMN:
          return "Getter";
        case SETTER_COLUMN:
          return "Setter";
        default:
          throw new RuntimeException("Incorrect column index");
      }
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) return true;
      if (myCheckedMarks[rowIndex]) {
        if (columnIndex == GETTER_COLUMN && myCbEncapsulateGet.isSelected()) return true;
        if (columnIndex == SETTER_COLUMN) {
          if (!myFinalMarks[rowIndex] && myCbEncapsulateSet.isSelected()) return true;
        }
      }
      return false;
    }

    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) {
        myCheckedMarks[rowIndex] = ((Boolean) aValue).booleanValue();
        fireTableRowsUpdated(rowIndex, rowIndex);
      } else {
        String name = (String) aValue;
        PsiField field = myFields[rowIndex];
        switch (columnIndex) {
          case GETTER_COLUMN:
            myGetterNames[rowIndex] = name;
            myGetterPrototypes[rowIndex] = generateMethodPrototype(field, name, true);
            break;

          case SETTER_COLUMN:
            mySetterNames[rowIndex] = name;
            mySetterPrototypes[rowIndex] = generateMethodPrototype(field, name, false);
            break;

          default:
            throw new RuntimeException("Incorrect column index");
        }
      }
    }
  }

  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/general/overridingMethod.png");
  private static final Icon IMPLEMENTING_METHOD_ICON = IconLoader.getIcon("/general/implementingMethod.png");
  private static final Icon EMPTY_OVERRIDE_ICON = EmptyIcon.create(16, 16);

  private class MyTableRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, final Object value,
                                                   boolean isSelected, boolean hasFocus, final int row,
                                                   final int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final int modelColumn = myTable.convertColumnIndexToModel(column);

      this.setIconTextGap(0);
      PsiField field = myFields[row];
      switch (modelColumn) {
        case FIELD_COLUMN:
          {
            Icon icon = field.getIcon(Iconable.ICON_FLAG_VISIBILITY);
            MyTableRenderer.this.setIcon(icon);
            MyTableRenderer.this.setDisabledIcon(icon);
            configureColors(isSelected, table, hasFocus, row, column);
            break;
          }

        case GETTER_COLUMN:
        case SETTER_COLUMN:
          {
            Icon methodIcon = IconUtilEx.getEmptyIcon(true);
            Icon overrideIcon = EMPTY_OVERRIDE_ICON;

            PsiMethod prototype = modelColumn == GETTER_COLUMN ? myGetterPrototypes[row] : mySetterPrototypes[row];
            if (prototype != null) {
//              MyTableRenderer.this.setForeground(Color.black);
              configureColors(isSelected, table, hasFocus, row, column);

              PsiMethod existing = myClass.findMethodBySignature(prototype, false);
              if (existing != null) {
                methodIcon = existing.getIcon(Iconable.ICON_FLAG_VISIBILITY);
              }

              PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(prototype, myClass);
              if (superMethods.length > 0) {
                if (!superMethods[0].hasModifierProperty(PsiModifier.ABSTRACT)) {
                  overrideIcon = OVERRIDING_METHOD_ICON;
                } else {
                  overrideIcon = IMPLEMENTING_METHOD_ICON;
                }
              }
            } else {
              MyTableRenderer.this.setForeground(Color.red);
            }

            RowIcon icon = new RowIcon(2);
            icon.setIcon(methodIcon, 0);
            icon.setIcon(overrideIcon, 1);
            MyTableRenderer.this.setIcon(icon);
            MyTableRenderer.this.setDisabledIcon(icon);
            break;
          }

        default:
          {
            MyTableRenderer.this.setIcon(null);
            MyTableRenderer.this.setDisabledIcon(null);
          }
      }
      boolean enabled = myCheckedMarks[row];
      if (enabled) {
        if (modelColumn == GETTER_COLUMN) {
          enabled = myCbEncapsulateGet.isSelected();
        } else if (modelColumn == SETTER_COLUMN) {
          enabled = !myFinalMarks[row] && myCbEncapsulateSet.isSelected();
        }
      }
      this.setEnabled(enabled);
      return this;
    }

    private void configureColors(boolean isSelected, JTable table, boolean hasFocus, final int row, final int column) {
      if (isSelected) {
        setForeground(table.getSelectionForeground());
      } else {
        setForeground(UIManager.getColor("Table.foreground"));
      }

      if (hasFocus) {
        if (table.isCellEditable(row, column)) {
          super.setForeground(UIManager.getColor("Table.focusCellForeground"));
          super.setBackground(UIManager.getColor("Table.focusCellBackground"));
        }
      }
    }
  }

}
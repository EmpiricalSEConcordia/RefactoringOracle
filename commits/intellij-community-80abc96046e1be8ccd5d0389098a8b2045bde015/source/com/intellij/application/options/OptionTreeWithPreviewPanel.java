package com.intellij.application.options;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author max
 */
public abstract class OptionTreeWithPreviewPanel extends CodeStyleAbstractPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.CodeStyleSpacesPanel");
  private JTree myOptionsTree;
  private HashMap myKeyToFieldMap = new HashMap();
  private ArrayList myKeys = new ArrayList();
  private final JPanel myPanel = new JPanel(new GridBagLayout());

  public OptionTreeWithPreviewPanel(CodeStyleSettings settings) {
    super(settings);

    initTables();

    myOptionsTree = createOptionsTree();
    myOptionsTree.setCellRenderer(new MyTreeCellRenderer());
    myPanel.add(new JScrollPane(myOptionsTree),
        new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                               new Insets(7, 7, 3, 4), 0, 0));

    JPanel previewPanel = new JPanel(new BorderLayout()) {
      public Dimension getPreferredSize() {
        return new Dimension(200, 0);
      }
    };
    previewPanel.setBorder(IdeBorderFactory.createTitledBorder("Preview"));

    myPanel.add(previewPanel,
                new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                       new Insets(0, 0, 0, 4), 0, 0));

    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);

  }

  protected JTree createOptionsTree() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    String groupName = "";
    DefaultMutableTreeNode groupNode = null;
    for (int i = 0; i < myKeys.size(); i++) {
      if (myKeys.get(i) instanceof BooleanOptionKey) {
        BooleanOptionKey key = (BooleanOptionKey)myKeys.get(i);
        String newGroupName = key.groupName;
        if (!newGroupName.equals(groupName) || groupNode == null) {
          groupName = newGroupName;
          groupNode = new DefaultMutableTreeNode(newGroupName);
          rootNode.add(groupNode);
        }
        groupNode.add(new MyToggleTreeNode(key, key.cbName));
      }
      else if (myKeys.get(i) instanceof IntSelectionOptionKey) {
        IntSelectionOptionKey key = (IntSelectionOptionKey)myKeys.get(i);
        String newGroupName = key.groupName;
        if (!newGroupName.equals(groupName) || groupNode == null) {
          groupName = newGroupName;
          groupNode = new DefaultMutableTreeNode(newGroupName);
          rootNode.add(groupNode);
        }
        MyToggleTreeNode[] nodes = new MyToggleTreeNode[key.rbNames.length];
        for (int j = 0; j < nodes.length; j++) {
          nodes[j] = new MyToggleTreeNode(key, key.rbNames[j]);
          groupNode.add(nodes[j]);
        }
        key.setCreatedNodes(nodes);
      }
    }

    DefaultTreeModel model = new DefaultTreeModel(rootNode);

    final Tree optionsTree = new Tree(model);
    TreeUtil.installActions(optionsTree);
    optionsTree.setRootVisible(false);
    optionsTree.putClientProperty("JTree.lineStyle", "Angled");
    optionsTree.setShowsRootHandles(true);


    optionsTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (!optionsTree.isEnabled()) return;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          TreePath treePath = optionsTree.getLeadSelectionPath();
          selectCheckbox(treePath);
          e.consume();
        }
      }
    });

    optionsTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!optionsTree.isEnabled()) return;
        TreePath treePath = optionsTree.getPathForLocation(e.getX(), e.getY());
        selectCheckbox(treePath);
      }
    });

    int row = 0;
    while (row < optionsTree.getRowCount()) {
      optionsTree.expandRow(row);
      row++;
    }

    return optionsTree;
  }

  private void selectCheckbox(TreePath treePath) {
    if (treePath == null) {
      return;
    }
    Object o = treePath.getLastPathComponent();
    if (o instanceof MyToggleTreeNode) {
      MyToggleTreeNode node = (MyToggleTreeNode)o;
      if (node.isCheckbox()) {
        node.setSelected(!node.isSelected());
      }
      else {
        MyToggleTreeNode[] group = node.getGroup();
        for (int i = 0; i < group.length; i++) {
          MyToggleTreeNode groupNode = group[i];
          groupNode.setSelected(false);
          int row = myOptionsTree.getRowForPath(new TreePath(groupNode.getPath()));
          myOptionsTree.repaint(myOptionsTree.getRowBounds(row));
        }
        node.setSelected(true);
      }
      int row = myOptionsTree.getRowForPath(treePath);
      myOptionsTree.repaint(myOptionsTree.getRowBounds(row));
      updatePreview();
    }
  }

  protected LexerEditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return HighlighterFactory.createJavaHighlighter(scheme, LanguageLevel.HIGHEST);
  }

  protected abstract void initTables();

  protected int getRightMargin() {
    return -1;
  }

  protected abstract String getPreviewText();

  protected void resetImpl(final CodeStyleSettings settings) {
    TreeModel treeModel = myOptionsTree.getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    resetNode(root, settings);
  }

  private void resetNode(TreeNode node, final CodeStyleSettings settings) {
    if (node instanceof MyToggleTreeNode) {
      resetMyTreeNode((MyToggleTreeNode)node, settings);
      return;
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      resetNode(child, settings);
    }
  }

  private void resetMyTreeNode(MyToggleTreeNode childNode, final CodeStyleSettings settings) {
    try {
      if (childNode.getKey() instanceof BooleanOptionKey) {
        BooleanOptionKey key = (BooleanOptionKey)childNode.getKey();
        Field field = (Field)myKeyToFieldMap.get(key);
        childNode.setSelected(field.getBoolean(settings));
      }
      else if (childNode.getKey() instanceof IntSelectionOptionKey) {
        IntSelectionOptionKey key = (IntSelectionOptionKey)childNode.getKey();
        Field field = (Field)myKeyToFieldMap.get(key);
        int fieldValue = field.getInt(settings);
        for (int i = 0; i < key.rbNames.length; i++) {
          if (childNode.getText().equals(key.rbNames[i])) {
            childNode.setSelected(fieldValue == key.values[i]);
          }
        }
      }
    }
    catch (IllegalArgumentException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
  }

  public void apply(CodeStyleSettings settings) {
    TreeModel treeModel = myOptionsTree.getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    applyNode(root, settings);
  }

  private void applyNode(TreeNode node, final CodeStyleSettings settings) {
    if (node instanceof MyToggleTreeNode) {
      applyToggleNode((MyToggleTreeNode)node, settings);
      return;
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      applyNode(child, settings);
    }
  }

  private void applyToggleNode(MyToggleTreeNode childNode, final CodeStyleSettings settings) {
    try {
      if (childNode.getKey() instanceof BooleanOptionKey) {
        BooleanOptionKey key = (BooleanOptionKey)childNode.getKey();
        Field field = (Field)myKeyToFieldMap.get(key);
        field.set(settings, childNode.isSelected() ? Boolean.TRUE : Boolean.FALSE);
      }
      else if (childNode.getKey() instanceof IntSelectionOptionKey) {
        if (!childNode.isSelected()) return;
        IntSelectionOptionKey key = (IntSelectionOptionKey)childNode.getKey();
        Field field = (Field)myKeyToFieldMap.get(key);
        for (int i = 0; i < key.rbNames.length; i++) {
          if (childNode.getText().equals(key.rbNames[i])) {
            field.set(settings, new Integer(key.values[i]));
            break;
          }
        }
      }
    }
    catch (IllegalArgumentException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
  }

  public boolean isModified(CodeStyleSettings settings) {
    TreeModel treeModel = myOptionsTree.getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    if (isModified(root, settings)) {
      return true;
    }
    return false;

  }

  private boolean isModified(TreeNode node, final CodeStyleSettings settings) {
    if (node instanceof MyToggleTreeNode) {
      if (isToggleNodeModified((MyToggleTreeNode)node, settings)) {
        return true;
      }
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      if (isModified(child, settings)) {
        return true;
      }
    }
    return false;
  }

  private boolean isToggleNodeModified(MyToggleTreeNode childNode, final CodeStyleSettings settings) {
    try {
      if (childNode.getKey() instanceof BooleanOptionKey) {
        BooleanOptionKey key = (BooleanOptionKey)childNode.getKey();
        Field field = (Field)myKeyToFieldMap.get(key);
        return childNode.isSelected() != field.getBoolean(settings);
      }
      else if (childNode.getKey() instanceof IntSelectionOptionKey) {
        if (!childNode.isSelected()) return false;
        IntSelectionOptionKey key = (IntSelectionOptionKey)childNode.getKey();
        Field field = (Field)myKeyToFieldMap.get(key);
        for (int i = 0; i < key.rbNames.length; i++) {
          if (childNode.getText().equals(key.rbNames[i])) {
            return field.getInt(settings) != key.values[i];
          }
        }
      }
    }
    catch (IllegalArgumentException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    return false;
  }

  protected void initBooleanField(String fieldName, String cbName, String groupName) {
    try {
      Class styleSettingsClass = CodeStyleSettings.class;
      Field field = styleSettingsClass.getField(fieldName);
      BooleanOptionKey key = new BooleanOptionKey(groupName, cbName);
      myKeyToFieldMap.put(key, field);
      myKeys.add(key);
    }
    catch (NoSuchFieldException e) {
    }
    catch (SecurityException e) {
    }
  }

  protected void initRadioGroupField(String fieldName, String groupName, String[] rbNames, int[] values) {
    try {
      Class styleSettingsClass = CodeStyleSettings.class;
      Field field = styleSettingsClass.getField(fieldName);
      IntSelectionOptionKey key = new IntSelectionOptionKey(groupName, rbNames, values);
      myKeyToFieldMap.put(key, field);
      myKeys.add(key);
    }
    catch (NoSuchFieldException e) {
    }
    catch (SecurityException e) {
    }
  }

  protected class MyTreeCellRenderer implements TreeCellRenderer {
    private MyLabelPanel myLabel;
    private JCheckBox myCheckBox;
    private JRadioButton myRadioButton;

    public MyTreeCellRenderer() {
      myLabel = new MyLabelPanel();
      myCheckBox = new JCheckBox();
      myRadioButton = new JRadioButton();
      myCheckBox.setMargin(new Insets(0, 0, 0, 0));
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {

      if (value instanceof MyToggleTreeNode) {
        MyToggleTreeNode treeNode = (MyToggleTreeNode)value;
        JToggleButton button = treeNode.isCheckbox() ? (JToggleButton)myCheckBox : (JToggleButton)myRadioButton;
        button.setText(treeNode.getText());
        button.setSelected(treeNode.isSelected);
        if (isSelected) {
          button.setForeground(UIManager.getColor("Tree.selectionForeground"));
          button.setBackground(UIManager.getColor("Tree.selectionBackground"));
        }
        else {
          button.setForeground(UIManager.getColor("Tree.textForeground"));
          button.setBackground(UIManager.getColor("Tree.textBackground"));
        }

        button.setEnabled(tree.isEnabled());

        return button;
      }
      else {
        Font font = tree.getFont();
        Font boldFont = new Font(font.getName(), Font.BOLD, font.getSize());
        myLabel.setFont(boldFont);
        myLabel.setText(value.toString());

        if (isSelected) {
          myLabel.setForeground(UIManager.getColor("Tree.selectionForeground"));
          Color backColor = UIManager.getColor("Tree.selectionBackground");
          myLabel.setBackground(backColor);
        }
        else {
          myLabel.setForeground(UIManager.getColor("Tree.textForeground"));
          myLabel.setBackground(UIManager.getColor("Tree.textBackground"));
        }

        myLabel.setEnabled(tree.isEnabled());

        return myLabel;
      }
    }
  }

  private static class MyLabelPanel extends JPanel {
    private String myText = "";
    private boolean hasFocus = false;

    public MyLabelPanel() {
    }

    public void setText(String text) {
      myText = text;
      if (myText == null) {
        myText = "";
      }
    }

    protected void paintComponent(Graphics g) {
      g.setFont(getMyFont());
      FontMetrics fontMetrics = getFontMetrics(getMyFont());
      int h = fontMetrics.getHeight();
      int w = fontMetrics.charsWidth(myText.toCharArray(), 0, myText.length());
      g.setColor(getBackground());
      g.fillRect(0, 1, w + 2, h);
      if (hasFocus) {
        g.setColor(UIManager.getColor("Tree.textBackground"));
        g.drawRect(0, 1, w + 2, h);
      }
      g.setColor(getForeground());
      g.drawString(myText, 2, h - fontMetrics.getDescent() + 1);
    }

    private Font getMyFont() {
      Font font = UIManager.getFont("Tree.font");
      return new Font(font.getName(), Font.BOLD, font.getSize());
    }

    public Dimension getPreferredSize() {
      FontMetrics fontMetrics = getFontMetrics(getMyFont());
      if (fontMetrics == null) {
        return new Dimension(0, 0);
      }
      int h = fontMetrics.getHeight();
      int w = fontMetrics.charsWidth(myText.toCharArray(), 0, myText.length());
      return new Dimension(w + 4, h + 2);
    }
  }

  private static class BooleanOptionKey {
    final String groupName;
    final String cbName;

    public BooleanOptionKey(String groupName, String cbName) {
      this.groupName = groupName;
      this.cbName = cbName;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof BooleanOptionKey)) return false;
      BooleanOptionKey key = (BooleanOptionKey)obj;
      return groupName.equals(key.groupName) && cbName.equals(key.cbName);
    }

    public int hashCode() {
      return cbName.hashCode();
    }
  }

  private static class IntSelectionOptionKey {
    final String groupName;
    final String[] rbNames;
    final int[] values;
    private MyToggleTreeNode[] myNodes;

    public IntSelectionOptionKey(String groupName, String[] rbNames, int[] values) {
      this.groupName = groupName;
      this.rbNames = rbNames;
      this.values = values;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof IntSelectionOptionKey)) return false;

      final IntSelectionOptionKey intSelectionOptionKey = (IntSelectionOptionKey)o;

      if (!groupName.equals(intSelectionOptionKey.groupName)) return false;
      if (!Arrays.equals(rbNames, intSelectionOptionKey.rbNames)) return false;

      return true;
    }

    public int hashCode() {
      return groupName.hashCode() + rbNames[0].hashCode() * 29;
    }

    public void setCreatedNodes(MyToggleTreeNode[] nodes) { myNodes = nodes; }

    public MyToggleTreeNode[] getNodes() { return myNodes; }
  }

  private static class MyToggleTreeNode extends DefaultMutableTreeNode {
    private Object myKey;
    private String myText;
    private boolean isSelected;
    private boolean isCheckbox;

    public MyToggleTreeNode(Object key, String text) {
      myKey = key;
      myText = text;
      isCheckbox = true;
    }

    public Object getKey() { return myKey; }

    public String getText() { return myText; }

    public boolean isCheckbox() { return isCheckbox; }

    public void setSelected(boolean val) { isSelected = val; }

    public boolean isSelected() { return isSelected; }

    public MyToggleTreeNode[] getGroup() {
      return ((IntSelectionOptionKey)myKey).getNodes();
    }
  }

  protected FileType getFileType() {
    return StdFileTypes.JAVA;
  }

  public JComponent getInternalPanel() {
    return myPanel;
  }
}

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;

public abstract class OrderPanel<T> extends JPanel{
  private String CHECKBOX_COLUMN_NAME = "Export";

  private final Class<T> myEntryClass;
  private final JTable myEntryTable;

  private final java.util.List<OrderPanelListener> myListeners = new ArrayList<OrderPanelListener>();

  private boolean myEntryEditable = false;

  protected OrderPanel(Class<T> entryClass) {
    this(entryClass, true);
  }

  protected OrderPanel(Class<T> entryClass, boolean showSheckboxes) {
    super(new BorderLayout());

    myEntryClass = entryClass;

    myEntryTable = new Table(new MyTableModel(showSheckboxes));
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setShowHorizontalLines(false);
    myEntryTable.setShowVerticalLines(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));

    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    myEntryTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if(getCheckboxColumn() == -1) return;

          final int[] selectedRows = myEntryTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (int idx = 0; idx < selectedRows.length; idx++) {
            final int selectedRow = selectedRows[idx];
            if (selectedRow < 0 || !myEntryTable.isCellEditable(selectedRow, getCheckboxColumn())) {
              return;
            }
            currentlyMarked &= ((Boolean)myEntryTable.getValueAt(selectedRow, getCheckboxColumn())).booleanValue();
          }
          for (int idx = 0; idx < selectedRows.length; idx++) {
            myEntryTable.setValueAt(currentlyMarked? Boolean.FALSE : Boolean.TRUE, selectedRows[idx], getCheckboxColumn());
          }
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );

    add(ScrollPaneFactory.createScrollPane(myEntryTable), BorderLayout.CENTER);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0,0);
    }
  }

  public void setEntriesEditable(boolean entryEditable) {
    myEntryEditable = entryEditable;
  }

  public void setCheckboxColumnName(String name) {
    final int width;
    if(name == null) {
      width = 0;
      CHECKBOX_COLUMN_NAME = "";
    }
    else {
      CHECKBOX_COLUMN_NAME = name;
      final FontMetrics fontMetrics = myEntryTable.getFontMetrics(myEntryTable.getFont());
      width = fontMetrics.stringWidth(" " + name + " ") + 4;
    }

    final TableColumn checkboxColumn = myEntryTable.getTableHeader().getColumnModel().getColumn(getCheckboxColumn());
    checkboxColumn.setWidth(width);
    checkboxColumn.setPreferredWidth(width);
    checkboxColumn.setMaxWidth(width);
    checkboxColumn.setMinWidth(width);
  }

  public void moveSelectedItemsUp() {
    myEntryTable.requestFocus();
    TableUtil.moveSelectedItemsUp(myEntryTable);
    for (Iterator<OrderPanelListener> iterator = myListeners.iterator(); iterator.hasNext();) {
      OrderPanelListener orderPanelListener = iterator.next();
      orderPanelListener.entryMoved();
    }
  }

  public void moveSelectedItemsDown() {
    myEntryTable.requestFocus();
    TableUtil.moveSelectedItemsDown(myEntryTable);
    for (Iterator<OrderPanelListener> iterator = myListeners.iterator(); iterator.hasNext();) {
      OrderPanelListener orderPanelListener = iterator.next();
      orderPanelListener.entryMoved();
    }
  }

  public void addListener(OrderPanelListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(OrderPanelListener listener) {
    myListeners.remove(listener);
  }

  public JTable getEntryTable() {
    return myEntryTable;
  }

  public void clear() {
    MyTableModel model = ((MyTableModel)myEntryTable.getModel());
    while(model.getRowCount() > 0){
      model.removeRow(0);
    }
  }

  public void remove(T orderEntry) {
    MyTableModel model = ((MyTableModel)myEntryTable.getModel());
    int rowCount = model.getRowCount();
    for (int i = 0; i < rowCount; i++) {
        if(getValueAt(i) == orderEntry) {
          model.removeRow(i);
          return;
        }
    }
  }

  public void add(T orderEntry) {
    MyTableModel model = ((MyTableModel)myEntryTable.getModel());
    if(getCheckboxColumn() == -1) {
      model.addRow(new Object[]{orderEntry});
    }
    else {
      model.addRow(new Object[]{isChecked(orderEntry) ? Boolean.TRUE : Boolean.FALSE, orderEntry});
    }
  }

  protected int getEntryColumn() {
    return ((MyTableModel)myEntryTable.getModel()).getEntryColumn();
  }

  private int getCheckboxColumn() {
    return ((MyTableModel)myEntryTable.getModel()).getCheckboxColumn();
  }

  private class MyTableModel extends DefaultTableModel {
    private final boolean myShowCheckboxes;
    public MyTableModel(boolean showCheckboxes) {
      myShowCheckboxes = showCheckboxes;
    }

    private int getEntryColumn() {
      return getColumnCount() - 1;
    }

    private int getCheckboxColumn() {
      return getColumnCount() - 2;
    }

    public String getColumnName(int column) {
      if (column == getEntryColumn()) {
        return "";
      }
      if (column == getCheckboxColumn()) {
        return CHECKBOX_COLUMN_NAME;
      }
      return null;
    }

    public Class getColumnClass(int column) {
      if (column == getEntryColumn()) {
        return myEntryClass;
      }
      if (column == getCheckboxColumn()) {
        return Boolean.class;
      }
      return super.getColumnClass(column);
    }

    public int getColumnCount() {
      return myShowCheckboxes ? 2 : 1;
    }

    public boolean isCellEditable(int row, int column) {
      if (column == getCheckboxColumn()) {
        return isCheckable(OrderPanel.this.getValueAt(row));
      }
      return myEntryEditable;
    }

    public void setValueAt(Object aValue, int row, int column) {
      super.setValueAt(aValue, row, column);
      if (column == getCheckboxColumn()) {
        setChecked(OrderPanel.this.getValueAt(row), ((Boolean)aValue).booleanValue());
      }
    }
  }

  public T getValueAt(int row) {
    return (T)((MyTableModel)myEntryTable.getModel()).getValueAt(row, getEntryColumn());
  }

  public abstract boolean isCheckable(T entry);
  public abstract boolean isChecked  (T entry);
  public abstract void    setChecked (T entry, boolean checked);

  public java.util.List<T> getEntries() {
    final TableModel model = myEntryTable.getModel();
    final int size = model.getRowCount();
    java.util.List<T> result = new ArrayList<T>(size);
    for (int idx = 0; idx < size; idx++) {
      result.add(getValueAt(idx));
    }

    return result;
  }


}

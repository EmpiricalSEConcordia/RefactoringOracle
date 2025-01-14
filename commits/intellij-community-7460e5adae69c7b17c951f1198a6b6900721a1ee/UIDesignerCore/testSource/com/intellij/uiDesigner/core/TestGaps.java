package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public final class TestGaps extends TestCase{
  public void test1() {
    final JPanel panel = new JPanel(new GridLayoutManager(1,2, new Insets(0,0,0,0), 10, 0));

    final JTextField field1 = new JTextField();
    field1.setPreferredSize(new Dimension(100,20));
    final JTextField field2 = new JTextField();
    field2.setPreferredSize(new Dimension(100,20));

    panel.add(field1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(field2, new GridConstraints(0,1,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(210, preferredSize.width);
  }
  
  /**
   * field (span 2) | field (span 1)
   */ 
  public void test2() {
    final JPanel panel = new JPanel(new GridLayoutManager(1,3, new Insets(0,0,0,0), 7, 0));

    final JTextField field1 = new JTextField();
    field1.setPreferredSize(new Dimension(100,20));
    final JTextField field2 = new JTextField();
    field2.setPreferredSize(new Dimension(100,20));

    panel.add(field1, new GridConstraints(0,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(field2, new GridConstraints(0,2,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(207, preferredSize.width);
    
    panel.setSize(panel.getPreferredSize());
    panel.doLayout();
    
    assertEquals(new Rectangle(0,0,100,20), field1.getBounds()); 
    assertEquals(new Rectangle(107,0,100,20), field2.getBounds()); 
  }
  
  
  /**
   * 
   * btn1   |    btn2  | btn4
   *  btn3 (span 2)    | 
   */ 
  public void test3() {
    final JPanel panel = new JPanel(new GridLayoutManager(2,3, new Insets(0,0,0,0), 7, 0));

    final JButton btn1 = new JButton();
    btn1.setPreferredSize(new Dimension(100,20));
    final JButton btn2 = new JButton();
    btn2.setPreferredSize(new Dimension(100,20));
    final JButton btn3 = new JButton();
    btn3.setPreferredSize(new Dimension(100,20));
    final JButton btn4 = new JButton();
    btn4.setPreferredSize(new Dimension(100,20));
    
    panel.add(btn1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn2, new GridConstraints(0,1,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn3, new GridConstraints(1,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
    
    panel.add(btn4, new GridConstraints(0,2,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
    
    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(314, preferredSize.width);
    
    panel.setSize(panel.getPreferredSize());
    panel.doLayout();
  }

  /**
   *
   * btn1   |    btn2  | btn4
   *  btn3 (span 2)    |
   */
  public void test3a() {
    final JPanel panel = new JPanel(new GridLayoutManager(2,3, new Insets(0,0,0,0), 1000, 0));

    final JButton btn1 = new JButton();
    btn1.setPreferredSize(new Dimension(100,20));
    final JButton btn2 = new JButton();
    btn2.setPreferredSize(new Dimension(100,20));
    final JButton btn3 = new JButton();
    btn3.setPreferredSize(new Dimension(100,20));
    final JButton btn4 = new JButton();
    btn4.setPreferredSize(new Dimension(100,20));

    panel.add(btn1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn2, new GridConstraints(0,1,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn3, new GridConstraints(1,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn4, new GridConstraints(0,2,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(2300, preferredSize.width);

    panel.setSize(panel.getPreferredSize());
    panel.doLayout();
  }

  /**
   *
   * btn1   |    btn2
   *  btn3 (span 2)
   */
  public void test3b() {
    final JPanel panel = new JPanel(new GridLayoutManager(2,3, new Insets(0,0,0,0), 1000, 0));

    final JButton btn1 = new JButton();
    btn1.setPreferredSize(new Dimension(100,20));
    final JButton btn2 = new JButton();
    btn2.setPreferredSize(new Dimension(100,20));
    final JButton btn3 = new JButton();
    btn3.setPreferredSize(new Dimension(100,20));

    panel.add(btn1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn2, new GridConstraints(0,1,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn3, new GridConstraints(1,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(1200, preferredSize.width);

    panel.setSize(panel.getPreferredSize());
    panel.doLayout();
  }

  /**
   * btn1
   * -----
   * empty
   * ----
   * btn2 
   */ 
  public void test4() {
    final JPanel panel = new JPanel(new GridLayoutManager(3,1, new Insets(0,0,0,0), 0, 7));

    final JButton btn1 = new JButton();
    btn1.setPreferredSize(new Dimension(100,20));
    final JButton btn2 = new JButton();
    btn2.setPreferredSize(new Dimension(100,20));
    
    panel.add(btn1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn2, new GridConstraints(2,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(47, preferredSize.height);
    
    panel.setSize(panel.getPreferredSize());
    panel.doLayout();
  }
  
  /**
   * btn1
   * -----
   * spacer
   * ----
   * btn2 
   */ 
  public void test5() {
    final JPanel panel = new JPanel(new GridLayoutManager(3,1, new Insets(0,0,0,0), 0, 7));

    final JButton btn1 = new JButton();
    btn1.setPreferredSize(new Dimension(100,20));
    final JButton btn2 = new JButton();
    btn2.setPreferredSize(new Dimension(100,20));
    
    panel.add(btn1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(new Spacer(), new GridConstraints(1,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null));

    panel.add(btn2, new GridConstraints(2,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(47, preferredSize.height);
    
    panel.setSize(panel.getPreferredSize());
    panel.doLayout();
  }

  /**
   * btn1
   * ----- (very big gap)
   * btn2
   */
  public void test6() {
    final JPanel panel = new JPanel(new GridLayoutManager(2,1, new Insets(0,0,0,0), 0, 500));

    final JButton btn1 = new JButton();
    btn1.setPreferredSize(new Dimension(100,20));
    final JButton btn2 = new JButton();
    btn2.setPreferredSize(new Dimension(100,20));

    panel.add(btn1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    panel.add(btn2, new GridConstraints(1,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(540, preferredSize.height);

    panel.setSize(panel.getPreferredSize());
    panel.doLayout(); // should not crash
  }

}

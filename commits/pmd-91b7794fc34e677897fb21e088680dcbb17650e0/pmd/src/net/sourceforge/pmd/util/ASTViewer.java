package net.sourceforge.pmd.util;

import net.sourceforge.pmd.ast.ASTCompilationUnit;
import net.sourceforge.pmd.ast.JavaParser;
import net.sourceforge.pmd.ast.ParseException;
import net.sourceforge.pmd.ast.SimpleNode;
import net.sourceforge.pmd.jaxen.DocumentNavigator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Iterator;

import org.jaxen.BaseXPath;
import org.jaxen.XPath;
import org.jaxen.JaxenException;

public class ASTViewer {

    private static class JSmartPanel extends JPanel {

        private GridBagConstraints constraints = new GridBagConstraints();

        public JSmartPanel() {
            super(new GridBagLayout());
        }

        public void add(Component comp, int gridx, int gridy, int gridwidth, int gridheight, double weightx, double weighty, int anchor, int fill, Insets insets) {
            constraints.gridx = gridx;
            constraints.gridy = gridy;
            constraints.gridwidth = gridwidth;
            constraints.gridheight = gridheight;
            constraints.weightx = weightx;
            constraints.weighty = weighty;
            constraints.anchor = anchor;
            constraints.fill = fill;
            constraints.insets = insets;

            add(comp, constraints);
        }
    }

    private static class MyPrintStream extends PrintStream {

        public MyPrintStream() {
            super(System.out);
        }

        private StringBuffer buf = new StringBuffer();

        public void println(String s) {
            super.println(s);
            buf.append(s);
            buf.append(System.getProperty("line.separator"));
        }

        public String getString() {
            return buf.toString();
        }
    }

    private class ShowListener implements ActionListener {
        public void actionPerformed(ActionEvent ae) {
            StringReader sr = new StringReader(codeEditorPane.getText());
            JavaParser parser = new JavaParser(sr);
            MyPrintStream ps = new MyPrintStream();
            System.setOut(ps);
            try {
                ASTCompilationUnit c = parser.CompilationUnit();
                c.dump("");
                astArea.setText(ps.getString());
            } catch (ParseException pe) {
                astArea.setText(pe.fillInStackTrace().getMessage());
            }
        }
    }

    private class XPathListener implements ActionListener {
        public void actionPerformed(ActionEvent ae) {
            if (xpathQueryField.getText().length() == 0) {
                xpathResultArea.setText("XPath query field is empty");
                codeEditorPane.requestFocus();
                return;
            }
            StringReader sr = new StringReader(codeEditorPane.getText());
            JavaParser parser = new JavaParser(sr);
            try {
                XPath xpath = new BaseXPath(xpathQueryField.getText(), new DocumentNavigator());
                ASTCompilationUnit c = parser.CompilationUnit();
                StringBuffer sb = new StringBuffer();
                for (Iterator iter = xpath.selectNodes(c).iterator(); iter.hasNext();) {
                    SimpleNode node = (SimpleNode) iter.next();
                    String name = node.getClass().getName().substring(node.getClass().getName().lastIndexOf('.')+1);
                    String line = " at line " + String.valueOf(node.getBeginLine());
                    sb.append(name).append(line).append(System.getProperty("line.separator"));
                }
                xpathResultArea.setText(sb.toString());
                if (sb.length() == 0) {
                    xpathResultArea.setText("No results returned " + System.currentTimeMillis());
                }
            } catch (ParseException pe) {
                xpathResultArea.setText(pe.fillInStackTrace().getMessage());
            } catch (JaxenException je) {
                xpathResultArea.setText(je.fillInStackTrace().getMessage());
            }
            xpathQueryField.requestFocus();
        }
    }

    private JTextPane codeEditorPane = new JTextPane();
    private JTextArea astArea = new JTextArea();
    private JTextArea xpathResultArea = new JTextArea();
    private JTextField xpathQueryField = new JTextField(40);
    private JFrame frame = new JFrame("AST Viewer");

    public ASTViewer() {
        JSmartPanel codePanel = new JSmartPanel();
        JScrollPane codeScrollPane = new JScrollPane(codeEditorPane);
        codePanel.add(codeScrollPane, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0));

        JSmartPanel astPanel = new JSmartPanel();
        astArea.setRows(20);
        astArea.setColumns(20);
        JScrollPane astScrollPane = new JScrollPane(astArea);
        astPanel.add(astScrollPane, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0));

        JSmartPanel xpathResultPanel = new JSmartPanel();
        xpathResultArea.setRows(20);
        xpathResultArea.setColumns(20);
        JScrollPane xpathResultScrollPane = new JScrollPane(xpathResultArea);
        xpathResultPanel.add(xpathResultScrollPane, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0));

        JButton goButton = new JButton("Go");
        goButton.setMnemonic('g');
        goButton.addActionListener(new ShowListener());
        goButton.addActionListener(new XPathListener());

        JPanel controlPanel = new JPanel();
        controlPanel.add(new JLabel("XPath Query (if any) ->"));
        controlPanel.add(xpathQueryField);
        controlPanel.add(goButton);

        JSplitPane resultsSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, astPanel, xpathResultPanel);
        JSplitPane upperSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, codePanel, resultsSplitPane);
        JSplitPane containerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upperSplitPane, controlPanel);

        frame.getContentPane().add(containerSplitPane);

        frame.setSize(1000, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        int screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
        frame.setLocation((screenWidth/2) - frame.getWidth()/2, (screenHeight/2) - frame.getHeight()/2);
        frame.setVisible(true);
        frame.show();

        containerSplitPane.setDividerLocation(containerSplitPane.getMaximumDividerLocation() - (containerSplitPane.getMaximumDividerLocation()/4));
        upperSplitPane.setDividerLocation(upperSplitPane.getMaximumDividerLocation() / 3);
        codeEditorPane.setSize(upperSplitPane.getMaximumDividerLocation() / 3, containerSplitPane.getMaximumDividerLocation() - (containerSplitPane.getMaximumDividerLocation()/4));
    }

    public static void main(String[] args) {
        new ASTViewer();
    }
}

/*
 * Class RemoteConfigurable
 * @author Jeka
 */
package com.intellij.execution.remote;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.ui.ConfigurationArgumentsHelpArea;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.*;

public class RemoteConfigurable extends SettingsEditor<RemoteConfiguration> {
  JPanel myPanel;
  private JRadioButton myRbSocket;
  private JRadioButton myRbShmem;
  private JRadioButton myRbListen;
  private JRadioButton myRbAttach;
  private JTextField myAddressField;
  private JTextField myHostField;
  private JTextField myPortField;
  private JPanel myShmemPanel;
  private JPanel mySocketPanel;
  private ConfigurationArgumentsHelpArea myHelpArea;
  @NonNls private ConfigurationArgumentsHelpArea myJDK13HelpArea;
  private String myHostName = "";
  @NonNls
  protected static final String LOCALHOST = "localhost";

  public RemoteConfigurable() {
    myJDK13HelpArea.setLabelText(ExecutionBundle.message("environment.variables.helper.use.arguments.jdk13.label"));
    
    final ButtonGroup transportGroup = new ButtonGroup();
    transportGroup.add(myRbSocket);
    transportGroup.add(myRbShmem);

    final ButtonGroup connectionGroup = new ButtonGroup();
    connectionGroup.add(myRbListen);
    connectionGroup.add(myRbAttach);

    final DocumentListener helpTextUpdater = new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateHelpText();
      }
    };
    myAddressField.getDocument().addDocumentListener(helpTextUpdater);
    myHostField.getDocument().addDocumentListener(helpTextUpdater);
    myPortField.getDocument().addDocumentListener(helpTextUpdater);
    myRbSocket.setSelected(true);
    final ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Object source = e.getSource();
        if (source.equals(myRbSocket)) {
           myShmemPanel.setVisible(false);
           mySocketPanel.setVisible(true);
        }
        else if (source.equals(myRbShmem)) {
           myShmemPanel.setVisible(true);
           mySocketPanel.setVisible(false);
        }
        myPanel.repaint();
        updateHelpText();
      }
    };
    myRbShmem.addActionListener(listener);
    myRbSocket.addActionListener(listener);

    final ItemListener updateListener = new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        final boolean isAttach = myRbAttach.isSelected();

        if(!isAttach && myHostField.isEditable()) {
          myHostName = myHostField.getText();
        }

        myHostField.setEditable(isAttach);
        myHostField.setEnabled(isAttach);

        myHostField.setText(isAttach ? myHostName : LOCALHOST);
        updateHelpText();
      }
    };
    myRbAttach.addItemListener(updateListener);
    myRbListen.addItemListener(updateListener);

    final FocusListener fieldFocusListener = new FocusAdapter() {
      public void focusLost(final FocusEvent e) {
        updateHelpText();
      }
    };
    myAddressField.addFocusListener(fieldFocusListener);
    myPortField.addFocusListener(fieldFocusListener);
  }

  public void applyEditorTo(@NotNull final RemoteConfiguration configuration) throws ConfigurationException {
    configuration.HOST = (myHostField.isEditable() ? myHostField.getText() : myHostName).trim();
    if ("".equals(configuration.HOST)) {
      configuration.HOST = null;
    }
    configuration.PORT = myPortField.getText().trim();
    if ("".equals(configuration.PORT)) {
      configuration.PORT = null;
    }
    configuration.SHMEM_ADDRESS = myAddressField.getText().trim();
    if ("".equals(configuration.SHMEM_ADDRESS)) {
      configuration.SHMEM_ADDRESS = null;
    }
    configuration.USE_SOCKET_TRANSPORT = myRbSocket.isSelected();
    configuration.SERVER_MODE = myRbListen.isSelected();
  }

  public void resetEditorFrom(final RemoteConfiguration configuration) {
    if (!SystemInfo.isWindows) {
      configuration.USE_SOCKET_TRANSPORT = true;
      myRbShmem.setEnabled(false);
      myAddressField.setEditable(false);
    }
    myAddressField.setText(configuration.SHMEM_ADDRESS);
    myHostName = configuration.HOST;
    myHostField.setText(configuration.HOST);
    myPortField.setText(configuration.PORT);
    if (configuration.USE_SOCKET_TRANSPORT) {
      myRbSocket.doClick();
    }
    else {
      myRbShmem.doClick();
    }
    if (configuration.SERVER_MODE) {
      myRbListen.doClick();
    }
    else {
      myRbAttach.doClick();
    }
    myRbShmem.setEnabled(SystemInfo.isWindows);
  }

  @NotNull
  public JComponent createEditor() {
    return myPanel;
  }

  public void disposeEditor() {
  }

  private void updateHelpText() {
    boolean useSockets = !myRbShmem.isSelected();

    final RemoteConnection connection = new RemoteConnection(
      useSockets,
      myHostName,
      useSockets ? myPortField.getText().trim() : myAddressField.getText().trim(),
      myRbListen.isSelected()
    );
    final String cmdLine = connection.getLaunchCommandLine();
    
    myHelpArea.updateText(cmdLine);
    myJDK13HelpArea.updateText("-Xnoagent -Djava.compiler=NONE " + cmdLine);
  }


}
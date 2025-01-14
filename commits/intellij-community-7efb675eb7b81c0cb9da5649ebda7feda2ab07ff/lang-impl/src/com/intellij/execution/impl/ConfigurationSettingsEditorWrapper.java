package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.UnknownRunConfiguration;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Map;

/**
 * User: anna
 * Date: 27-Mar-2006
 */
public class ConfigurationSettingsEditorWrapper extends SettingsEditor<RunnerAndConfigurationSettingsImpl> {
  public static DataKey<ConfigurationSettingsEditorWrapper> CONFIGURATION_EDITOR_KEY = DataKey.create("ConfigurationSettingsEditor");
  private JPanel myComponentPlace;
  private JCheckBox myCbStoreProjectConfiguration;
  private JPanel myWholePanel;

  private JPanel myStepsPanel;
  private Map<Key<? extends BeforeRunTask>, BeforeRunTask> myStepsBeforeLaunch;
  private Map<Key<? extends BeforeRunTask>, StepBeforeLaunchRow> myStepBeforeLaunchRows = new THashMap<Key<? extends BeforeRunTask>, StepBeforeLaunchRow>();

  private boolean myStoreProjectConfiguration;

  private final ConfigurationSettingsEditor myEditor;

  public ConfigurationSettingsEditorWrapper(final RunnerAndConfigurationSettingsImpl settings) {
    myEditor = new ConfigurationSettingsEditor(settings);
    Disposer.register(this, myEditor);
    doReset(settings);
  }

  private void doReset(RunnerAndConfigurationSettingsImpl settings) {
    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());

    myStepsBeforeLaunch = runManager.getBeforeRunTasks(runConfiguration);
    myStepBeforeLaunchRows.clear();

    final BeforeRunTaskProvider<BeforeRunTask>[] providers = Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME,
                                                                        runConfiguration.getProject());
    myStepsPanel.removeAll();
    if (providers.length == 0 || runConfiguration instanceof UnknownRunConfiguration) {
      myStepsPanel.setVisible(false);
    }
    else {
      myStepsPanel.setLayout(new GridLayout(myStepsBeforeLaunch.size(), 1));
      for (BeforeRunTaskProvider<BeforeRunTask> provider : providers) {
        final BeforeRunTask task = myStepsBeforeLaunch.get(provider.getId());
        if (task != null) {
          final StepBeforeLaunchRow stepRow = new StepBeforeLaunchRow(runConfiguration, provider, task);
          myStepBeforeLaunchRows.put(provider.getId(), stepRow);
          myStepsPanel.add(stepRow);
        }
      }
    }

    myStoreProjectConfiguration = runManager.isConfigurationShared(settings);
    myCbStoreProjectConfiguration.setEnabled(!(runConfiguration instanceof UnknownRunConfiguration));
    myCbStoreProjectConfiguration.setSelected(myStoreProjectConfiguration);
    myCbStoreProjectConfiguration.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myStoreProjectConfiguration = myCbStoreProjectConfiguration.isSelected();
      }
    });
  }

  @NotNull
  protected JComponent createEditor() {
    myComponentPlace.setLayout(new GridBagLayout());
    myComponentPlace.add(myEditor.getComponent(), new GridBagConstraints(0,0,1,1,1.0,1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));
    myComponentPlace.doLayout();
    myWholePanel.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new TypeSafeDataProviderAdapter(new MyDataProvider()));
    return myWholePanel;
  }

  public void updateBeforeRunTaskPanel(@NotNull Key<? extends BeforeRunTask> key) {
    myStepBeforeLaunchRows.get(key).update(myStepsBeforeLaunch.get(key));
  }

  protected void disposeEditor() {
  }

  public void resetEditorFrom(final RunnerAndConfigurationSettingsImpl settings) {
    myEditor.resetEditorFrom(settings);
    doReset(settings);
  }

  public void applyEditorTo(final RunnerAndConfigurationSettingsImpl settings) throws ConfigurationException {
    myEditor.applyEditorTo(settings);
    doApply(settings);
  }

  public RunnerAndConfigurationSettingsImpl getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettingsImpl result = myEditor.getSnapshot();
    doApply(result);
    return result;
  }

  private void doApply(final RunnerAndConfigurationSettingsImpl settings) {
    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());
    runManager.setBeforeRunTasks(runConfiguration, myStepsBeforeLaunch);
    runManager.shareConfiguration(runConfiguration, myStoreProjectConfiguration);
  }

  public Map<Key<? extends BeforeRunTask>, BeforeRunTask> getStepsBeforeLaunch() {
    return Collections.unmodifiableMap(myStepsBeforeLaunch);
  }

  public boolean isStoreProjectConfiguration() {
    return myStoreProjectConfiguration;
  }

  private class StepBeforeLaunchRow extends JPanel {
    private final JCheckBox myCheckBox;
    private FixedSizeButton myButton;
    private final RunConfiguration myRunConfiguration;
    private final BeforeRunTaskProvider<BeforeRunTask> myProvider;

    public StepBeforeLaunchRow(final RunConfiguration runConfiguration, final BeforeRunTaskProvider<BeforeRunTask> provider,
                               final BeforeRunTask beforeRunTask) {
      super(new GridBagLayout());
      myRunConfiguration = runConfiguration;
      myProvider = provider;
      final boolean isChecked = beforeRunTask.isEnabled();
      myCheckBox = new JCheckBox(provider.getDescription(runConfiguration, beforeRunTask), isChecked);
      GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0 , 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0);
      add(myCheckBox, gc);
      gc.weightx = 1;
      if (provider.hasConfigurationButton()) {
        myButton = new FixedSizeButton(20);
        add(myButton, gc);

        myButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            provider.configureTask(runConfiguration, beforeRunTask);
            myCheckBox.setText(provider.getDescription(runConfiguration, beforeRunTask));
          }
        });
      }
      else {
        add(Box.createHorizontalBox(), gc);
      }
      enableSettings(beforeRunTask);
      myCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          beforeRunTask.setEnabled(myCheckBox.isSelected());
          enableSettings(beforeRunTask);
        }
      });
    }

    private void enableSettings(final BeforeRunTask task) {
      if (myButton != null) {
        myButton.setEnabled(myCheckBox.isSelected());
      }
      myCheckBox.setText(myProvider.getDescription(myRunConfiguration, task));
    }

    public void update(BeforeRunTask task) {
      myCheckBox.setSelected(task.isEnabled());
      enableSettings(task);
    }
  }

  private class MyDataProvider implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (key.equals(CONFIGURATION_EDITOR_KEY)) {
        sink.put(CONFIGURATION_EDITOR_KEY, ConfigurationSettingsEditorWrapper.this);
      }
    }
  }
}

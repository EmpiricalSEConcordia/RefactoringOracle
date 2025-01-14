package com.intellij.ide.wizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.CommandButtonGroup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractWizard extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.AbstractWizard");

  private int myCurrentStep;
  protected final ArrayList<Step> mySteps;
  private JButton myPreviousButton;
  private JButton myNextButton;
  private JButton myFinishButton;
  private JButton myCancelButton;
  private JButton myHelpButton;
  private JPanel myContentPanel;
  private JLabel myIconLabel;
  private Component myCurrentStepComponent;

  public AbstractWizard(final String title, final Component dialogParent) {
    super(dialogParent, true);
    mySteps = new ArrayList<Step>();
    initWizard(title);
  }

  public AbstractWizard(final String title, final Project project) {
    super(project, true);
    mySteps = new ArrayList<Step>();
    initWizard(title);
  }

  private void initWizard(final String title) {
    setTitle(title);
    myCurrentStep = 0;
    myPreviousButton = new JButton("< Previous");
    myPreviousButton.setMnemonic('P');
    myNextButton = new JButton("Next >");
    myNextButton.setMnemonic('N');
    myFinishButton = new JButton("Finish");
    myFinishButton.setMnemonic('F');
    myCancelButton = new JButton("Cancel");
    myHelpButton = new JButton("Help");
    myContentPanel = new JPanel(new CardLayout());

    myIconLabel = new JLabel();

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          helpAction();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          helpAction();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );
  }

  protected JComponent createSouthPanel() {
    final CommandButtonGroup panel = new CommandButtonGroup();
    panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

    if (mySteps.size() > 1) {
      panel.addButton(myPreviousButton);
      panel.addButton(myNextButton);
    }
    panel.addButton(myFinishButton);
    panel.addButton(myCancelButton);
    panel.addButton(myHelpButton);

    myPreviousButton.setEnabled(false);
    myPreviousButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        doPreviousAction();
      }
    });
    myNextButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        doNextAction();
      }
    });
    myFinishButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          // Commit data of current step and perform OK action
          final Step currentStep = mySteps.get(myCurrentStep);
          LOG.assertTrue(currentStep != null);
          try {
            currentStep._commit(true);
            doOKAction();
          }
          catch (final CommitStepException exc) {
            exc.printStackTrace();
            Messages.showErrorDialog(
              myContentPanel,
              exc.getMessage(),
              "Error"
            );
          }
        }
      }
    );
    myCancelButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          doCancelAction();
        }
      }
    );
    myHelpButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        helpAction();
      }
    });

    return panel;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    panel.add(myIconLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    panel.add(myContentPanel, new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 5, 0, 0), 0, 0));
    return panel;
  }

  public int getCurrentStep() {
    return myCurrentStep;
  }

  protected Step getCurrentStepObject() {
    return mySteps.get(myCurrentStep);
  }

  public void addStep(final Step step) {
    mySteps.add(step);

    // card layout is used
    final Component component = step.getComponent();
    if (component != null) {
      addStepComponent(component);
    }

    if (mySteps.size() > 1) {
      myFinishButton.setText("Finish");
      myFinishButton.setMnemonic('F');
    }
    else {
      myFinishButton.setText("OK");
      myFinishButton.setMnemonic('O');
    }
  }

  protected void init() {
    super.init();
    updateStep();
  }

  private final Map<Component, String> myComponentToIdMap = new HashMap<Component, String>();

  private String addStepComponent(final Component component) {
    String id = myComponentToIdMap.get(component);
    if (id == null) {
      id = Integer.toString(myComponentToIdMap.size());
      myComponentToIdMap.put(component, id);
      myContentPanel.add(component, id);
    }
    return id;
  }

  private void showStepComponent(final Component component) {
    String id = myComponentToIdMap.get(component);
    if (id == null) {
      id = addStepComponent(component);
      myContentPanel.revalidate();
      myContentPanel.repaint();
    }
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, id);
  }

  protected void doPreviousAction() {
    // Commit data of current step
    final Step currentStep = mySteps.get(myCurrentStep);
    LOG.assertTrue(currentStep != null);
    try {
      currentStep._commit(false);
    }
    catch (final CommitStepException exc) {
      Messages.showErrorDialog(
        myContentPanel,
        exc.getMessage(),
        "Error"
      );
      return;
    }

    myCurrentStep = getPreviousStep(myCurrentStep);
    updateStep();
  }

  protected void doNextAction() {
    // Commit data of current step
    final Step currentStep = mySteps.get(myCurrentStep);
    LOG.assertTrue(currentStep != null);
    try {
      currentStep._commit(false);
    }
    catch (final CommitStepException exc) {
      Messages.showErrorDialog(
        myContentPanel,
        exc.getMessage(),
        "Error"
      );
      return;
    }

    myCurrentStep = getNextStep(myCurrentStep);
    updateStep();
  }

  /**
   * override this to provide alternate step order
   */
  protected int getNextStep(int step) {
    final int stepCount = mySteps.size();
    if (++step >= stepCount) {
      step = stepCount - 1;
    }
    return step;
  }

  /**
   * override this to provide alternate step order
   */
  protected int getPreviousStep(int step) {
    if (--step < 0) {
      step = 0;
    }
    return step;
  }


  protected void updateStep() {
    if (mySteps.size() == 0) {
      return;
    }

    final Step step = mySteps.get(myCurrentStep);
    LOG.assertTrue(step != null);
    step._init();
    myCurrentStepComponent = step.getComponent();
    LOG.assertTrue(myCurrentStepComponent != null);
    showStepComponent(myCurrentStepComponent);

    final Icon icon = step.getIcon();
    myIconLabel.setIcon(icon);
    if (icon != null) {
      myIconLabel.setSize(icon.getIconWidth(), icon.getIconHeight());
    }
    else {
      myIconLabel.setSize(0, 0);
    }
    myNextButton.setEnabled(mySteps.size() == 1 || myCurrentStep < mySteps.size() - 1);
    myPreviousButton.setEnabled(myCurrentStep > 0);
  }

  protected JButton getNextButton() {
    return myNextButton;
  }

  protected JButton getPreviousButton() {
    return myPreviousButton;
  }

  protected JButton getFinishButton() {
    return myFinishButton;
  }

  public Component getCurrentStepComponent() {
    return myCurrentStepComponent;
  }

  protected void helpAction() {
    HelpManager.getInstance().invokeHelp(getHelpID());
  }

  protected int getNumberOfSteps() {
    return mySteps.size();
  }

  protected abstract String getHelpID();

  protected boolean isCurrentStep(final Step step) {
    if (step == null) {
      return false;
    }
    return getCurrentStepComponent() == step.getComponent();
  }
}
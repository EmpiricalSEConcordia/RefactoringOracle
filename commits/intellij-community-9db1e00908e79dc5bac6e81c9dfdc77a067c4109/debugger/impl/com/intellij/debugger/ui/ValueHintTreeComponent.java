package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.InspectDebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHintTreeComponent;

/**
 * @author nik
*/
class ValueHintTreeComponent extends AbstractValueHintTreeComponent<Pair<NodeDescriptorImpl, String>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.ValueHintTreeComponent");
  private final ValueHint myValueHint;
  private final InspectDebuggerTree myTree;

  public ValueHintTreeComponent(final ValueHint valueHint, InspectDebuggerTree tree, final String title) {
    super(valueHint, tree, Pair.create(tree.getInspectDescriptor(), title));
    myValueHint = valueHint;
    myTree = tree;
  }


  protected void updateTree(Pair<NodeDescriptorImpl, String> descriptorWithTitle){
    final NodeDescriptorImpl descriptor = descriptorWithTitle.first;
    final String title = descriptorWithTitle.second;
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(myValueHint.getProject())).getContext();
    context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
      public void threadAction() {
        myTree.setInspectDescriptor(descriptor);
        myValueHint.showTreePopup(myTree, context, title, ValueHintTreeComponent.this);
      }
    });
  }


  protected void setNodeAsRoot(final Object node) {
    if (node instanceof DebuggerTreeNodeImpl) {
      myValueHint.shiftLocation();
      final DebuggerTreeNodeImpl debuggerTreeNode = (DebuggerTreeNodeImpl)node;
      final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(myValueHint.getProject())).getContext();
      context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
        public void threadAction() {
          try {
            final NodeDescriptorImpl descriptor = debuggerTreeNode.getDescriptor();
            final TextWithImports evaluationText = DebuggerTreeNodeExpression.createEvaluationText(debuggerTreeNode, context);
            final String title = evaluationText.getText();
            addToHistory(Pair.create(descriptor, title));
            myTree.setInspectDescriptor(descriptor);
            myValueHint.showTreePopup(myTree, context, title, ValueHintTreeComponent.this);
          }
          catch (final EvaluateException e1) {
            LOG.debug(e1);
          }
        }
      });
    }
  }

}

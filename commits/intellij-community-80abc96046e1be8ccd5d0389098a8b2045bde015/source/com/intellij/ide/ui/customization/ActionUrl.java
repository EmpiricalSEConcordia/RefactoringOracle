package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.*;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * User: anna
 * Date: Mar 18, 2005
 */
public class ActionUrl implements JDOMExternalizable {
  public static final int ADDED = 1;
  public static final int DELETED = -1;

  //temp action only
  public static final int MOVE = 2;

  private ArrayList<String> myGroupPath;
  private Object myComponent;
  private int myActionType;
  private int myAbsolutePosition;


  public int myInitialPosition = -1;

  private static final String IS_GROUP = "is_group";
  private static final String SEPERATOR = "seperator";
  private static final String IS_ACTION = "is_action";
  private static final String VALUE = "value";
  private static final String PATH = "path";
  private static final String ACTION_TYPE = "action_type";
  private static final String POSITION = "position";


  public ActionUrl() {
    myGroupPath = new ArrayList<String>();
  }

  public ActionUrl(final ArrayList<String> groupPath,
                   final Object component,
                   final int actionType,
                   final int position) {
    myGroupPath = groupPath;
    myComponent = component;
    myActionType = actionType;
    myAbsolutePosition = position;
  }

  public ArrayList<String> getGroupPath() {
    return myGroupPath;
  }

  public String getParentGroup(){
    return myGroupPath.get(myGroupPath.size() - 1);
  }

  public Object getComponent() {
    return myComponent;
  }

  public AnAction getComponentAction(){
    if (myComponent instanceof Separator){
      return Separator.getInstance();
    }
    if (myComponent instanceof String){
      return ActionManager.getInstance().getAction((String)myComponent);
    }
    if (myComponent instanceof Group){
      final String id = ((Group)myComponent).getId();
      if (id == null || id.length() == 0){
        return ((Group)myComponent).constructActionGroup(true);
      }
      return ActionManager.getInstance().getAction(id);
    }
    return null;
  }

  public int getActionType() {
    return myActionType;
  }

  public void setActionType(final int actionType) {
    myActionType = actionType;
  }

  public int getAbsolutePosition() {
    return myAbsolutePosition;
  }

  public void setAbsolutePosition(final int absolutePosition) {
    myAbsolutePosition = absolutePosition;
  }

  public int getInitialPosition() {
    return myInitialPosition;
  }

  public void setInitialPosition(final int initialPosition) {
    myInitialPosition = initialPosition;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myGroupPath = new ArrayList<String>();
    for (Iterator<Element> iterator = element.getChildren(PATH).iterator(); iterator.hasNext();) {
      Element o = iterator.next();
      myGroupPath.add(o.getAttributeValue(VALUE));
    }
    final String attributeValue = element.getAttributeValue(VALUE);
    if (element.getAttributeValue(IS_ACTION) != null) {
      myComponent = attributeValue;
    }
    else if (element.getAttributeValue(SEPERATOR) != null) {
      myComponent = Separator.getInstance();
    }
    else if (element.getAttributeValue(IS_GROUP) != null) {
      final AnAction action = ActionManager.getInstance().getAction(attributeValue);
      myComponent = action != null && action instanceof ActionGroup ? ActionsTreeUtil.createGroup((ActionGroup)action, true) : new Group(attributeValue, attributeValue, null, null);
    }
    myActionType = Integer.parseInt(element.getAttributeValue(ACTION_TYPE));
    myAbsolutePosition = Integer.parseInt(element.getAttributeValue(POSITION));
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<String> iterator = myGroupPath.iterator(); iterator.hasNext();) {
      String s = iterator.next();
      Element path = new Element(PATH);
      path.setAttribute(VALUE, s);
      element.addContent(path);
    }
    if (myComponent instanceof String) {
      element.setAttribute(VALUE, (String)myComponent);
      element.setAttribute(IS_ACTION, "true");
    }
    else if (myComponent instanceof Separator) {
      element.setAttribute(SEPERATOR, "true");
    }
    else if (myComponent instanceof Group) {
      final String groupId = ((Group)myComponent).getId() != null && !((Group)myComponent).getId().equals("") ? ((Group)myComponent).getId() : ((Group)myComponent).getName();
      element.setAttribute(VALUE, groupId != null ? groupId : "");
      element.setAttribute(IS_GROUP, "true");
    }
    element.setAttribute(ACTION_TYPE, Integer.toString(myActionType));
    element.setAttribute(POSITION, Integer.toString(myAbsolutePosition));
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean isGroupContainsInPath(ActionGroup group){
    for (Iterator<String> iterator = myGroupPath.iterator(); iterator.hasNext();) {
      String s = iterator.next();
      if (s.equals(group.getTemplatePresentation().getText())){
        return true;
      }
    }
    return false;
  }

  public static void changePathInActionsTree(JTree tree, ActionUrl url){
     if (url.myActionType == ADDED){
      addPathToActionsTree(tree, url);
    } else if (url.myActionType == DELETED) {
      removePathFromActionsTree(tree, url);
    } else if (url.myActionType == MOVE){
      movePathInActionsTree(tree, url);
    }
  }

  private static void addPathToActionsTree(JTree tree, ActionUrl url) {
    final TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    if (treePath == null) return;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
    final int absolutePosition = url.getAbsolutePosition();
    if (node.getChildCount() > absolutePosition && absolutePosition >= 0) {
      if (url.getComponent() instanceof Group){
        node.insert(ActionsTreeUtil.createNode((Group)url.getComponent()), absolutePosition);
      } else {
        node.insert(new DefaultMutableTreeNode(url.getComponent()), absolutePosition);
      }
    }
  }

  private static void removePathFromActionsTree(JTree tree, ActionUrl url) {
    if (url.myComponent == null) return;
    final TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    if (treePath == null) return;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
    final int absolutePosition = url.getAbsolutePosition();
    if (node.getChildCount() > absolutePosition && absolutePosition >= 0) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(absolutePosition);
      if (child.getUserObject().equals(url.getComponent())) {
        node.remove(child);
      }
    }
  }

  private static void movePathInActionsTree(JTree tree, ActionUrl url){
    final TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    if (treePath != null){
      if (treePath.getLastPathComponent() != null){
        final DefaultMutableTreeNode parent = ((DefaultMutableTreeNode)treePath.getLastPathComponent());
        if (parent.getChildCount() > url.getInitialPosition()) {
          final DefaultMutableTreeNode child = (DefaultMutableTreeNode)parent.getChildAt(url.getInitialPosition());
          if (child.getUserObject().equals(url.getComponent())){
            parent.remove(child);
            parent.insert(child, url.getAbsolutePosition());
          }
        }
      }
    }
  }

  public static ArrayList<String> getGroupPath(final TreePath treePath){
    final ArrayList<String> result = new ArrayList<String>();
    for (int i = 0; i < treePath.getPath().length - 1; i++) {
      Object o = ((DefaultMutableTreeNode)treePath.getPath()[i]).getUserObject();
      if (o instanceof Group){
        result.add(((Group)o).getName());
      }
    }
    return result;
  }

  public boolean equals(Object object){
    if (!(object instanceof ActionUrl)){
      return false;
    }
    ActionUrl url = (ActionUrl)object;
    Object comp = myComponent instanceof Pair ? ((Pair)myComponent).first : myComponent;
    Object thatComp = url.myComponent instanceof Pair ? ((Pair)url.myComponent).first : url.myComponent;
    return Comparing.equal(comp, thatComp) && myGroupPath.equals(url.myGroupPath) && myAbsolutePosition == url.getAbsolutePosition();
  }

  public int hashCode() {
    int result = myComponent != null ? myComponent.hashCode() : 0;
    result += 29 * myGroupPath.hashCode();
    return result;
  }

  public void setComponent(final Object object) {
    myComponent = object;
  }

  public void setGroupPath(final ArrayList<String> groupPath) {
    myGroupPath = groupPath;
  }
}

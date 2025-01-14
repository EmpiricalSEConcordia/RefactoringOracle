/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class StackFrameProxyImpl extends JdiProxy implements StackFrameProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.StackFrameProxyImpl");
  private final ThreadReferenceProxyImpl myThreadProxy;
  private final int myFrameFromBottomIndex; // 1-based

  //caches
  private int myFrameIndex = -1;
  private StackFrame myStackFrame;
  private ObjectReference myThisReference;
  private Location myLocation;
  private ClassLoaderReference myClassLoader;
  private Boolean myIsObsolete = null;

  public StackFrameProxyImpl(ThreadReferenceProxyImpl threadProxy, StackFrame frame, int fromBottomIndex /* 1-based */) {
    super(threadProxy.getVirtualMachine());
    myThreadProxy = threadProxy;
    myFrameFromBottomIndex = fromBottomIndex;
    myStackFrame = frame;
    LOG.assertTrue(frame != null);
  }

  public boolean isObsolete() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myIsObsolete == null) {
      try {
        boolean isObsolete = (getVirtualMachine().versionHigher("1.4") && getStackFrame().location().method().isObsolete());
        myIsObsolete = new Boolean(isObsolete);
      }
      catch (InvalidStackFrameException e) {
        clearCaches();
        return isObsolete();
      }
    }
    return myIsObsolete.booleanValue();
  }

  protected void clearCaches() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    //DebuggerManagerThreadImpl.assertIsManagerThread();
    if (LOG.isDebugEnabled()) {
      LOG.debug("caches cleared " + super.toString());
    }
    myFrameIndex = -1;
    myStackFrame = null;
    myIsObsolete = null;
    myLocation      = null;
    myThisReference = null;
    myClassLoader = null;
  }

  /**
   * Use with caution. Better access stackframe data through the Proxy's methods
   * @return
   */

  public StackFrame getStackFrame() throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    checkValid();

    if(myStackFrame == null) {
      try {
        myStackFrame = myThreadProxy.getThreadReference().frame(getFrameIndex());
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }

    return myStackFrame;
  }

  public int getFrameIndex() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if(myFrameIndex == -1) {
      int count = myThreadProxy.frameCount();

      if(myFrameFromBottomIndex  > count) throw EvaluateExceptionUtil.createEvaluateException(new IncompatibleThreadStateException());
      
      myFrameIndex = count - myFrameFromBottomIndex;
    }
    return myFrameIndex;
  }

//  public boolean isProxiedFrameValid() {
//    if (myStackFrame != null) {
//      try {
//        myStackFrame.thread();
//        return true;
//      }
//      catch (InvalidStackFrameException e) {
//      }
//    }
//    return false;
//  }

  public VirtualMachineProxyImpl getVirtualMachine() {
    return (VirtualMachineProxyImpl) myTimer;
  }

  public Location location() throws EvaluateException {
    checkValid();
    if(myLocation == null) {
      try {
        myLocation = getStackFrame().location();
      }
      catch (InvalidStackFrameException e) {
        clearCaches();
        return location();
      }
    }
    return myLocation;
  }

  public ThreadReferenceProxyImpl threadProxy() {
    return myThreadProxy;
  }

  public String toString() {
    try {
      return "StackFrameProxyImpl: " + getStackFrame().toString();
    }
    catch (EvaluateException e) {
      return "StackFrameProxyImpl: " + e.getMessage() + "; frameFromBottom = " + myFrameFromBottomIndex + " threadName = " + threadProxy().name();
    }
  }

  public ObjectReference thisObject() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    try {
      if(myThisReference == null) {
        myThisReference = !isObsolete() ? getStackFrame().thisObject() : null;
      }
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      return thisObject();
    }
    catch (InternalException e) {
      if(e.errorCode() == 35) {
        LOG.debug(e); //bug in JDK 1.5 beta
        throw EvaluateExceptionUtil.createEvaluateException(e);
      } else {
        throw e;
      }
    }
    return myThisReference;
  }

  public List<LocalVariableProxyImpl> visibleVariables() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      List<LocalVariable> list = getStackFrame().visibleVariables();

      List<LocalVariableProxyImpl> locals = new ArrayList<LocalVariableProxyImpl>();
      for (Iterator<LocalVariable> iterator = list.iterator(); iterator.hasNext();) {
        LocalVariable localVariable = iterator.next();
        LOG.assertTrue(localVariable != null);
        locals.add(new LocalVariableProxyImpl(this, localVariable));
      }
      return locals;
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      return visibleVariables();
    }
    catch (AbsentInformationException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  public LocalVariableProxyImpl visibleVariableByName(String name) throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      LocalVariable variable = getStackFrame().visibleVariableByName(name);
      return variable != null ? new LocalVariableProxyImpl(this, variable) : null;
    }
    catch (InvalidStackFrameException e) {
      return visibleVariableByName(name);
    }
    catch (AbsentInformationException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  protected LocalVariable visibleVariableByNameInt(String name) throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      return getStackFrame().visibleVariableByName(name);
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      return visibleVariableByNameInt(name);
    }
    catch (AbsentInformationException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  public Value getValue(LocalVariableProxyImpl localVariable) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      return getStackFrame().getValue(localVariable.getVariable());
    }
    catch (InconsistentDebugInfoException e) {
      clearCaches();
      throw EvaluateExceptionUtil.INCONSISTEND_DEBUG_INFO;
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      return getValue(localVariable);
    }
  }

  public Map getValues(List list) throws EvaluateException{
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      return getStackFrame().getValues(list);
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      return getValues(list);
    }
  }

  public void setValue(LocalVariableProxyImpl localVariable, Value value) throws EvaluateException,
                                                                             ClassNotLoadedException,
                                                                             InvalidTypeException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      getStackFrame().setValue(localVariable.getVariable(), value);
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      setValue(localVariable, value);
    }
  }

  public int hashCode() {
    return myThreadProxy.hashCode() + myFrameFromBottomIndex;
  }


  public boolean equals(final Object obj) {
    if (!(obj instanceof StackFrameProxyImpl)) {
      return false;
    }
    StackFrameProxyImpl frameProxy = (StackFrameProxyImpl)obj;
    if(frameProxy == this)return true;

    return (myFrameFromBottomIndex == frameProxy.myFrameFromBottomIndex)  &&
           (myThreadProxy.equals(frameProxy.myThreadProxy));
  }

  public boolean isLocalVariableVisible(LocalVariableProxyImpl var) throws EvaluateException {
    try {
      return var.getVariable().isVisible(getStackFrame());
    }
    catch (IllegalArgumentException e) {
      // can be thrown if frame's method is different than variable's method
      throw EvaluateExceptionUtil.createEvaluateException("Internal error - frame's method is different than variable's method");
    }
  }

  public ClassLoaderReference getClassLoader() throws EvaluateException {
    if(myClassLoader == null) {
      myClassLoader = location().declaringType().classLoader();
    }
    return myClassLoader;
  }

  public boolean isBottom() {
    return myFrameFromBottomIndex == 1;
  }

}


/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 2, 2001
 * Time: 12:07:30 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefParameter;
import com.intellij.codeInspection.util.RefFilter;

public class RefUnreachableFilter extends RefFilter {
  public int getElementProblemCount(RefElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    return refElement.isSuspicious() ? 1 : 0;
  }
}

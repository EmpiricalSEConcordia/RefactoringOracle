/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 6, 2002
 * Time: 10:01:02 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class DfaRelationValue extends DfaValue {
  private DfaValue myLeftOperand;
  private DfaValue myRightOperand;
  private String myRelation;
  private boolean myIsNegated;

  public static class Factory {
    private final DfaRelationValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaRelationValue>> myStringToObject;
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaRelationValue(factory);
      myStringToObject = new HashMap<String, ArrayList<DfaRelationValue>>();
    }

    @Nullable
    public DfaRelationValue create(DfaValue dfaLeft, DfaValue dfaRight, @NonNls String relation, boolean negated) {
      if (dfaRight instanceof DfaTypeValue && !"instanceof".equals(relation)) return null;
      if ("+".equals(relation)) return null;

      if (dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaUnboxedValue
          || dfaRight instanceof DfaVariableValue || dfaRight instanceof DfaBoxedValue || dfaRight instanceof DfaUnboxedValue) {
        if (!(dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaUnboxedValue)) {
          return create(dfaRight, dfaLeft, getSymmetricOperation(relation), negated);
        }

        return createCanonicalRelation(relation, negated, dfaLeft, dfaRight);
      }
      if (dfaLeft instanceof DfaNotNullValue && dfaRight instanceof DfaConstValue) {
        return createCanonicalRelation(relation, negated, dfaLeft, dfaRight);
      }
      else if (dfaRight instanceof DfaNotNullValue && dfaLeft instanceof DfaConstValue) {
        return createCanonicalRelation(relation, negated, dfaRight, dfaLeft);
      }
      else {
        return null;
      }
    }

    private DfaRelationValue createCanonicalRelation(String relation,
                                                     boolean negated,
                                                     final DfaValue dfaLeft,
                                                     final DfaValue dfaRight) {
      // To canonical form.
      if ("!=".equals(relation)) {
        relation = "==";
        negated = !negated;
      }
      else if ("<".equals(relation)) {
        relation = ">=";
        negated = !negated;
      }
      else if ("<=".equals(relation)) {
        relation = ">";
        negated = !negated;
      }

      mySharedInstance.myLeftOperand = dfaLeft;
      mySharedInstance.myRightOperand = dfaRight;
      mySharedInstance.myRelation = relation;
      mySharedInstance.myIsNegated = negated;

      String id = mySharedInstance.toString();
      ArrayList<DfaRelationValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaRelationValue>();
        myStringToObject.put(id, conditions);
      }
      else {
        for (DfaRelationValue rel : conditions) {
          if (rel.hardEquals(mySharedInstance)) return rel;
        }
      }

      DfaRelationValue result = new DfaRelationValue(dfaLeft, dfaRight, relation, negated, myFactory);
      conditions.add(result);
      return result;
    }

    private static String getSymmetricOperation(String sign) {
      if ("<".equals(sign)) {
        return ">";
      }
      else if (">=".equals(sign)) {
        return "<=";
      }
      else if (">".equals(sign)) {
        return "<";
      }
      else if ("<=".equals(sign)) {
        return ">=";
      }

      return sign;
    }
  }

  private DfaRelationValue(DfaValueFactory factory) {
    super(factory);
  }

  private DfaRelationValue(DfaValue myLeftOperand, DfaValue myRightOperand, String myRelation, boolean myIsNegated,
                           DfaValueFactory factory) {
    super(factory);
    this.myLeftOperand = myLeftOperand;
    this.myRightOperand = myRightOperand;
    this.myRelation = myRelation;
    this.myIsNegated = myIsNegated;
  }

  public DfaValue getLeftOperand() {
    return myLeftOperand;
  }

  public DfaValue getRightOperand() {
    return myRightOperand;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  public DfaValue createNegated() {
    return myFactory.getRelationFactory().create(myLeftOperand, myRightOperand, myRelation, !myIsNegated);
  }

  private boolean hardEquals(DfaRelationValue rel) {
    return Comparing.equal(rel.myLeftOperand,myLeftOperand)
           && Comparing.equal(rel.myRightOperand,myRightOperand) &&
           rel.myRelation.equals(myRelation) &&
           rel.myIsNegated == myIsNegated;
  }

  @NonNls public String toString() {
    return (isNegated() ? "not " : "") + myLeftOperand + myRelation + myRightOperand;
  }
}

/*
 * Created on 20.07.2004
 */
package net.sourceforge.pmd.dfa;

import net.sourceforge.pmd.AbstractRule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.dfa.pathfinder.CurrentPath;
import net.sourceforge.pmd.dfa.pathfinder.DAAPathFinder;
import net.sourceforge.pmd.dfa.pathfinder.Executable;
import net.sourceforge.pmd.dfa.variableaccess.VariableAccess;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

/**
 * @author raik
 *         <p/>
 *         Starts path search for each method and runs code if found.
 */
public class DaaRule extends AbstractRule implements Executable {

    private RuleContext rc;
    private int counter;
    private static final int MAX_PATHS = 5000;

    public Object visit(ASTMethodDeclaration node, Object data) {
        this.rc = (RuleContext) data;
        counter = 0;

        IDataFlowNode n = (IDataFlowNode) node.getDataFlowNode().getFlow().get(0);
        System.out.println("In DaaRule, IDataFlowNode n = " + n);

        DAAPathFinder a = new DAAPathFinder(n, this);
        a.run();

        super.visit(node, data);
        return data;
    }

    public void execute(CurrentPath path) {
        Hashtable hash = new Hashtable();
        counter++;
        if (counter == 5000) {
            System.out.print("|");
            counter = 0;
        }
        for (Iterator d = path.iterator(); d.hasNext();) {
            IDataFlowNode inode = (IDataFlowNode) d.next();
            if (inode.getVariableAccess() != null) {
                for (int g = 0; g < inode.getVariableAccess().size(); g++) {
                    VariableAccess va = (VariableAccess) inode.getVariableAccess().get(g);

                    Object o = hash.get(va.getVariableName());
                    if (o != null) {
                        List array = (List) o;
                        int last = ((Integer) array.get(0)).intValue();
                        // TODO - at some point investigate and possibly reintroduce this line2 thing
                        //int line2 = ((Integer) array.get(1)).intValue();
/*
                        if (va.accessTypeMatches(last) && va.isDefinition()) { // DD
                            this.rc.getReport().addRuleViolation(createRuleViolation(rc, inode.getSimpleNode(), va.getVariableName(), "DD"));
                        } else if (last == VariableAccess.UNDEFINITION && va.isReference()) { // UR
                            this.rc.getReport().addRuleViolation(createRuleViolation(rc, inode.getSimpleNode(), va.getVariableName(), "UR"));
                        } else if (last == VariableAccess.DEFINITION && va.isUndefinition()) { // DU
                            this.rc.getReport().addRuleViolation(createRuleViolation(rc, inode.getSimpleNode(), va.getVariableName(), "DU"));
                        }
*/
                    }
                    List array = new ArrayList();
                    array.add(new Integer(va.getAccessType()));
                    array.add(new Integer(inode.getLine()));
                    hash.put(va.getVariableName(), array);
                }
            }
        }
    }
}

/*
 * Created on 14.07.2004
 */
package net.sourceforge.pmd.dfa.variableaccess;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.pmd.ast.ASTClassOrInterfaceBodyDeclaration;
import net.sourceforge.pmd.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.ast.ASTFormalParameter;
import net.sourceforge.pmd.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.ast.ASTVariableInitializer;
import net.sourceforge.pmd.ast.JavaParserVisitorAdapter;
import net.sourceforge.pmd.ast.SimpleNode;
import net.sourceforge.pmd.dfa.DataFlowNode;
import net.sourceforge.pmd.dfa.StartOrEndDataFlowNode;
import net.sourceforge.pmd.symboltable.NameOccurrence;
import net.sourceforge.pmd.symboltable.VariableNameDeclaration;

/**
 * @author raik, Sven Jacob
 *         <p/>
 *         Searches for special nodes and computes based on the sequence, the type of
 *         access of a variable.
 */
public class VariableAccessVisitor extends JavaParserVisitorAdapter {

    public void compute(ASTMethodDeclaration node) {
        if (node.jjtGetParent() instanceof ASTClassOrInterfaceBodyDeclaration) {
            this.computeNow(node);
        }
    }

    public void compute(ASTConstructorDeclaration node) {
        this.computeNow(node);
    }

    private void computeNow(SimpleNode node) {
	DataFlowNode inode = node.getDataFlowNode();

        List<VariableAccess> undefinitions = markUsages(inode);

        // all variables are first in state undefinition 
        DataFlowNode firstINode = inode.getFlow().get(0);
        firstINode.setVariableAccess(undefinitions);

        // all variables are getting undefined when leaving scope
        DataFlowNode lastINode = inode.getFlow().get(inode.getFlow().size() - 1);
        lastINode.setVariableAccess(undefinitions);
    }

    private List<VariableAccess> markUsages(DataFlowNode inode) {
        // undefinitions was once a field... seems like it works fine as a local
        List<VariableAccess> undefinitions = new ArrayList<VariableAccess>();
        Set<Map<VariableNameDeclaration, List<NameOccurrence>>> variableDeclarations = collectDeclarations(inode);
        for (Map<VariableNameDeclaration, List<NameOccurrence>> declarations: variableDeclarations) {
            for (Map.Entry<VariableNameDeclaration, List<NameOccurrence>> entry: declarations.entrySet()) {
                VariableNameDeclaration vnd = entry.getKey();

                if (vnd.getAccessNodeParent() instanceof ASTFormalParameter) {
                    // no definition/undefinition/references for parameters
                    continue;
                } else if (vnd.getAccessNodeParent().getFirstChildOfType(ASTVariableInitializer.class) != null) {
                    // add definition for initialized variables
                    addVariableAccess(
                            vnd.getNode(), 
                            new VariableAccess(VariableAccess.DEFINITION, vnd.getImage()), 
                            inode.getFlow());                    
                }
                undefinitions.add(new VariableAccess(VariableAccess.UNDEFINITION, vnd.getImage()));

                for (NameOccurrence occurrence: entry.getValue()) {
                    addAccess(occurrence, inode);
                }
            }
        }
        return undefinitions;
    }

    private Set<Map<VariableNameDeclaration, List<NameOccurrence>>> collectDeclarations(DataFlowNode inode) {
        Set<Map<VariableNameDeclaration, List<NameOccurrence>>> decls = new HashSet<Map<VariableNameDeclaration, List<NameOccurrence>>>();
        Map<VariableNameDeclaration, List<NameOccurrence>> varDecls;
        for (int i = 0; i < inode.getFlow().size(); i++) {
            DataFlowNode n = inode.getFlow().get(i);
            if (n instanceof StartOrEndDataFlowNode) {
                continue;
            }
            varDecls = n.getSimpleNode().getScope().getVariableDeclarations();
            if (!decls.contains(varDecls)) {
                decls.add(varDecls);
            }
        }
        return decls;
    }

    private void addAccess(NameOccurrence occurrence, DataFlowNode inode) {
        if (occurrence.isOnLeftHandSide()) {
            this.addVariableAccess(occurrence.getLocation(), new VariableAccess(VariableAccess.DEFINITION, occurrence.getImage()), inode.getFlow());
        } else if (occurrence.isOnRightHandSide() || (!occurrence.isOnLeftHandSide() && !occurrence.isOnRightHandSide())) {
            this.addVariableAccess(occurrence.getLocation(), new VariableAccess(VariableAccess.REFERENCING, occurrence.getImage()), inode.getFlow());
        }
    }

    /**
     * Adds a VariableAccess to a dataflow node.
     * @param node location of the access of a variable
     * @param va variable access to add
     * @param flow dataflownodes that can contain the node. 
     */
    private void addVariableAccess(SimpleNode node, VariableAccess va, List flow) {
        // backwards to find the right inode (not a method declaration) 
        for (int i = flow.size()-1; i > 0; i--) { 
            DataFlowNode inode = (DataFlowNode) flow.get(i);
            if (inode.getSimpleNode() == null) {
                continue;
            }

            List<? extends SimpleNode> children = inode.getSimpleNode().findChildrenOfType(node.getClass());
            for (SimpleNode n: children) {
                if (node.equals(n)) { 
                    List<VariableAccess> v = new ArrayList<VariableAccess>();
                    v.add(va);
                    inode.setVariableAccess(v);     
                    return;
                }
            }
        }
    }

}

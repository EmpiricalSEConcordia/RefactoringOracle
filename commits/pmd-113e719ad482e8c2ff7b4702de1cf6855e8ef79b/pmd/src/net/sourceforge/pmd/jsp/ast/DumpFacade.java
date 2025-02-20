package net.sourceforge.pmd.jsp.ast;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.pmd.lang.ast.Node;

public class DumpFacade extends JspParserVisitorAdapter {

    private PrintWriter writer;
    private boolean recurse;

    public void initializeWith(Writer writer, String prefix, boolean recurse, JspNode node) {
	this.writer = (writer instanceof PrintWriter) ? (PrintWriter) writer : new PrintWriter(writer);
	this.recurse = recurse;
	this.visit(node, prefix);
	try {
	    writer.flush();
	} catch (IOException e) {
	    throw new RuntimeException("Problem flushing PrintWriter.", e);
	}
    }

    @Override
    public Object visit(JspNode node, Object data) {
	dump(node, (String) data);
	if (recurse) {
	    return super.visit(node, data + " ");
	} else {
	    return data;
	}
    }

    private void dump(Node node, String prefix) {
	//
	// Dump format is generally composed of the following items...
	//

	// 1) Dump prefix
	writer.print(prefix);

	// 2) JJT Name of the Node
	writer.print(node.toString());

	//
	// If there are any additional details, then:
	// 1) A colon
	// 2) The Node.getImage() if it is non-empty
	// 3) Extras in parentheses
	//

	// Standard image handling
	String image = node.getImage();

	// Extras
	List<String> extras = new ArrayList<String>();

	// Other extras
	if (node instanceof ASTAttribute) {
	    extras.add("name=[" + ((ASTAttribute) node).getName() + "]");
	} else if (node instanceof ASTDeclaration) {
	    extras.add("name=[" + ((ASTDeclaration) node).getName() + "]");
	} else if (node instanceof ASTDoctypeDeclaration) {
	    extras.add("name=[" + ((ASTDoctypeDeclaration) node).getName() + "]");
	} else if (node instanceof ASTDoctypeExternalId) {
	    extras.add("uri=[" + ((ASTDoctypeExternalId) node).getUri() + "]");
	    if (((ASTDoctypeExternalId) node).getPublicId().length() > 0) {
		extras.add("publicId=[" + ((ASTDoctypeExternalId) node).getPublicId() + "]");
	    }
	} else if (node instanceof ASTElement) {
	    extras.add("name=[" + ((ASTElement) node).getName() + "]");
	    if (((ASTElement) node).isEmpty()) {
		extras.add("empty");
	    }
	} else if (node instanceof ASTJspDirective) {
	    extras.add("name=[" + ((ASTJspDirective) node).getName() + "]");
	} else if (node instanceof ASTJspDirectiveAttribute) {
	    extras.add("name=[" + ((ASTJspDirectiveAttribute) node).getName() + "]");
	    extras.add("value=[" + ((ASTJspDirectiveAttribute) node).getValue() + "]");
	}

	// Output image and extras
	if (image != null || !extras.isEmpty()) {
	    writer.print(":");
	    if (image != null) {
		writer.print(image);
	    }
	    for (String extra : extras) {
		writer.print("(");
		writer.print(extra);
		writer.print(")");
	    }
	}

	writer.println();
    }
}

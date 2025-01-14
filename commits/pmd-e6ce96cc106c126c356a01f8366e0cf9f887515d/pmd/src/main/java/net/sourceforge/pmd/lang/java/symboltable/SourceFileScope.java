/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.lang.java.symboltable;


import java.util.List;
import java.util.Map;

import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.symboltable.NameDeclaration;
import net.sourceforge.pmd.lang.symboltable.NameOccurrence;

/**
 * This scope is the outer most scope of a Java file.
 * A Source File can contain one ore more classes.
 */
public class SourceFileScope extends AbstractJavaScope {

    private String packageImage;
    private TypeSet types;

    public SourceFileScope() {
        this("");
    }

    public SourceFileScope(String packageImage) {
        this.packageImage = packageImage;
        this.types = new TypeSet();
        types.setASTCompilationUnitPackage(packageImage);
    }

    public void configureImports(List<ASTImportDeclaration> imports) {
        for (ASTImportDeclaration i : imports) {
            if (i.isImportOnDemand()) {
                types.addImport(i.getImportedName() + ".*");
            } else {
                types.addImport(i.getImportedName());
            }
        }
    }

    public Class<?> resolveType(String name) {
        try {
            return types.findClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public String getPackageName() {
        return packageImage;
    }

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException if declaration is not a {@link ClassNameDeclaration}
     */
    @Override
    public void addDeclaration(NameDeclaration declaration) {
        if (!(declaration instanceof ClassNameDeclaration)) {
            throw new IllegalArgumentException("A SourceFileScope can only contain classes.");
        }
        super.addDeclaration(declaration);
    }

    /**
     * Convenience method that casts the declarations to {@link ClassNameDeclaration}s.
     * @see #getDeclarations()
     * @return all class name declarations
     */
    public Map<ClassNameDeclaration, List<NameOccurrence>> getClassDeclarations() {
        return getDeclarations(ClassNameDeclaration.class);
    }

    public String toString() {
        return "SourceFileScope: " + glomNames(getClassDeclarations().keySet());
    }

    protected NameDeclaration findVariableHere(JavaNameOccurrence occ) {
        ImageFinderFunction finder = new ImageFinderFunction(occ.getImage());
        Applier.apply(finder, getDeclarations().keySet().iterator());
        return finder.getDecl();
    }
}

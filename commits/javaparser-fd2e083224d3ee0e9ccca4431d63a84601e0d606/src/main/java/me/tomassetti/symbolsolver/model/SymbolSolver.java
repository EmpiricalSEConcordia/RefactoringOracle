package me.tomassetti.symbolsolver.model;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;
import me.tomassetti.symbolsolver.model.declarations.ValueDeclaration;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;
import me.tomassetti.symbolsolver.model.javaparser.JavaParserFactory;
import me.tomassetti.symbolsolver.model.javaparser.UnsolvedSymbolException;
import me.tomassetti.symbolsolver.model.usages.MethodUsage;
import me.tomassetti.symbolsolver.model.usages.TypeUsage;

import java.util.List;
import java.util.Optional;

/**
 * Created by federico on 28/07/15.
 */
public class SymbolSolver {

    private TypeSolver typeSolver;

    public SymbolSolver(TypeSolver typeSolver){
        if (typeSolver == null) throw new IllegalArgumentException();

        this.typeSolver = typeSolver;
    }

    public SymbolReference<? extends ValueDeclaration> solveSymbol(String name, Context context) {
        return context.solveSymbol(name, typeSolver);
    }

    public SymbolReference<? extends ValueDeclaration> solveSymbol(String name, Node node) {
        return solveSymbol(name, JavaParserFactory.getContext(node));
    }

    public Optional<Value> solveSymbolAsValue(String name, Context context) {
        return context.solveSymbolAsValue(name, typeSolver);
    }

    public Optional<Value> solveSymbolAsValue(String name, Node node) {
        return solveSymbolAsValue(name, JavaParserFactory.getContext(node));
    }

    public SymbolReference<? extends TypeDeclaration> solveType(String name, Context context) {
        return context.solveType(name, typeSolver);
    }

    public SymbolReference<? extends TypeDeclaration> solveType(String name, Node node) {
        return solveType(name, JavaParserFactory.getContext(node));
    }

    public MethodUsage solveMethod(String methodName, List<TypeUsage> params, Context context) {
        //return context.solveMethod(methodName, params, typeSolver);
        //throw new UnsupportedOperationException();
        return new MethodUsage(context.solveMethod(methodName, params, typeSolver), typeSolver);
    }

    public MethodUsage solveMethod(String methodName, List<TypeUsage> params, Node node) {
        return solveMethod(methodName, params, JavaParserFactory.getContext(node));
    }
;
    public TypeDeclaration solveType(Type type) {
        if (type instanceof ReferenceType) {
            ReferenceType referenceType = (ReferenceType) type;
            // TODO consider array modifiers
            return solveType(referenceType.getType());
        } else if (type instanceof ClassOrInterfaceType) {

            //should call typesolver here!

            /*if (0 == 0) throw new RuntimeException(type.getParentNode().getParentNode().toString());

            ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
            SymbolReference<? extends ValueDeclaration> ref = solveSymbol(classType.getName(), type);
            if (!ref.isSolved()) {
                throw new UnsolvedSymbolException(JavaParserFactory.getContext(type), classType.getName());
            }
            if (!ref.getCorrespondingDeclaration().isType()) {
                throw new IllegalStateException(ref.getCorrespondingDeclaration().toString());
            }
            return ref.getCorrespondingDeclaration().asTypeDeclaration();*/
            String name = ((ClassOrInterfaceType) type).getName();
            SymbolReference<TypeDeclaration> ref = JavaParserFactory.getContext(type).solveType(name, typeSolver);
            if (!ref.isSolved()) {
                throw new UnsolvedSymbolException(JavaParserFactory.getContext(type), name);
            }
            return ref.getCorrespondingDeclaration();
        } else {
            throw new UnsupportedOperationException(type.getClass().getCanonicalName());
        }
    }
}

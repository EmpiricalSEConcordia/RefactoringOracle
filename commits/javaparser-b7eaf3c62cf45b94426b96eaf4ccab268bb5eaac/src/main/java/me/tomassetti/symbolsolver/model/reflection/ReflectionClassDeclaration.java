package me.tomassetti.symbolsolver.model.reflection;

import com.github.javaparser.ast.Node;
import me.tomassetti.symbolsolver.JavaParserFacade;
import me.tomassetti.symbolsolver.model.*;
import me.tomassetti.symbolsolver.model.declarations.ClassDeclaration;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;
import me.tomassetti.symbolsolver.model.usages.MethodUsage;
import me.tomassetti.symbolsolver.model.usages.NullTypeUsage;
import me.tomassetti.symbolsolver.model.usages.TypeUsageOfTypeDeclaration;
import me.tomassetti.symbolsolver.model.usages.TypeUsage;

import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by federico on 02/08/15.
 */
public class ReflectionClassDeclaration implements ClassDeclaration {

    private Class<?> clazz;

    public ReflectionClassDeclaration(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getQualifiedName() {
        return clazz.getCanonicalName();
    }

    @Override
    public Context getContext() {
        return new ClassOrInterfaceDeclarationContext(clazz);
    }

    @Override
    public SymbolReference<MethodDeclaration> solveMethod(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver) {
        List<MethodDeclaration> methods = new ArrayList<>();
        for (Method method : clazz.getMethods()) {
            MethodDeclaration methodDeclaration = new ReflectionMethodDeclaration(method);
            methods.add(methodDeclaration);
        }
        return MethodResolutionLogic.findMostApplicable(methods, name, parameterTypes, typeSolver);
    }

    @Override
    public String toString() {
        return "ReflectionClassDeclaration{" +
                "clazz=" + clazz.getCanonicalName() +
                '}';
    }

    @Override
    public TypeUsage getUsage(Node node) {
        for (TypeParameter tp : this.getTypeParameters()){
            throw new UnsupportedOperationException("Find parameters of "+this+" in "+node);
        }
        return new TypeUsageOfTypeDeclaration(this);
    }

    @Override
    public Optional<MethodUsage> solveMethodAsUsage(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver, Context invokationContext) {
        List<MethodDeclaration> methods = new ArrayList<>();
        for (Method method : clazz.getMethods()) {
            MethodDeclaration methodDeclaration = new ReflectionMethodDeclaration(method);
            methods.add(methodDeclaration);
        }
        SymbolReference<MethodDeclaration> ref = MethodResolutionLogic.findMostApplicable(methods, name, parameterTypes, typeSolver);
        if (ref.isSolved()) {
            return Optional.of(JavaParserFacade.get(typeSolver).convertToUsage(ref.getCorrespondingDeclaration(), getContext()));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean isAssignableBy(TypeUsage typeUsage) {
        if (typeUsage instanceof NullTypeUsage) {
            return true;
        }
        if (typeUsage instanceof LambdaTypeUsagePlaceholder) {
            return getQualifiedName().equals(Predicate.class.getCanonicalName()) ||
                    getQualifiedName().equals(Function.class.getCanonicalName());
        }
        if (typeUsage.isArray()) {
            return false;
        }
        if (typeUsage.isPrimitive()){
            return false;
        }
        if (!typeUsage.getTypeName().equals(getQualifiedName())){
            return false;
        }
        return true;
    }

    @Override
    public boolean isTypeVariable() {
        return false;
    }

    @Override
    public FieldDeclaration getField(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canBeAssignedBy(TypeDeclaration other) {
        if (getQualifiedName().equals(other.getQualifiedName())) {
            return true;
        }
        if (clazz.getSuperclass() != null) {
            if (new ReflectionClassDeclaration(clazz.getSuperclass()).canBeAssignedBy(other)){
                return true;
            }
        }
        for (Class<?> interfaze : clazz.getInterfaces()) {
            if (new ReflectionClassDeclaration(interfaze).canBeAssignedBy(other)){
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasField(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return clazz.getSimpleName();
    }

    @Override
    public boolean isField() {
        return false;
    }

    @Override
    public boolean isParameter() {
        return false;
    }

    @Override
    public boolean isType() {
        return true;
    }

    /*@Override
    public TypeDeclaration asTypeDeclaration() {
        return this;
    }

    @Override
    public TypeDeclaration getType() {
        throw new UnsupportedOperationException();
    }*/

    @Override
    public List<TypeParameter> getTypeParameters() {
        List<TypeParameter> params = new ArrayList<>();
        for (TypeVariable tv : this.clazz.getTypeParameters()) {
            params.add(new ReflectionTypeParameter(tv, true));
        }
        return params;
    }
}

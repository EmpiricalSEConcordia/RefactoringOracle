package me.tomassetti.symbolsolver.model.typesystem;

import me.tomassetti.symbolsolver.model.Context;
import me.tomassetti.symbolsolver.model.SymbolReference;
import me.tomassetti.symbolsolver.model.TypeParameter;
import me.tomassetti.symbolsolver.model.TypeSolver;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TypeUsageOfTypeParameter implements TypeUsage {

    private TypeParameter typeParameter;

    @Override
    public String toString() {
        return "TypeUsageOfTypeParameter{" +
                "typeParameter=" + typeParameter +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeUsageOfTypeParameter that = (TypeUsageOfTypeParameter) o;

        if (!typeParameter.equals(that.typeParameter)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return typeParameter.hashCode();
    }

    public TypeUsageOfTypeParameter(TypeParameter typeParameter) {
        this.typeParameter = typeParameter;
    }

    @Override
    public boolean isArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public TypeUsage replaceParam(String name, TypeUsage replaced) {
        if (name.equals(typeParameter.getName())) {
            return replaced;
        } else {
            return this;
        }
    }

    @Override
    public Optional<MethodUsage> solveMethodAsUsage(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver, Context invokationContext) {
        for (TypeParameter.Bound bound : typeParameter.getBounds(typeSolver)) {
            Optional<MethodUsage> methodUsage = bound.getType().solveMethodAsUsage(name, parameterTypes, typeSolver, invokationContext);
            if (methodUsage.isPresent()) {
                return methodUsage;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isReferenceType() {
        return false;
    }

    @Override
    public String getTypeName() {
        return typeParameter.getName();
    }

    @Override
    public SymbolReference<MethodDeclaration> solveMethod(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TypeUsage> parameters() {
        return Collections.emptyList();
    }

    @Override
    public TypeParameter asTypeParameter() {
        return typeParameter;
    }

    @Override
    public boolean isTypeVariable() {
        return true;
    }

    @Override
    public boolean isAssignableBy(TypeUsage other, TypeSolver typeSolver) {
        if (other.isTypeVariable()) {
            return getTypeName().equals(other.getTypeName());
        } else {
            return false;
        }
    }

    @Override
    public String getQualifiedName() {
        return getTypeName();
    }

    @Override
    public String prettyPrint() {
        return typeParameter.getName();
    }
}

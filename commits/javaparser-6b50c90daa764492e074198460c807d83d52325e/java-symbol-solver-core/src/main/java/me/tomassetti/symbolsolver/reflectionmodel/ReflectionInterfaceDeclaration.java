package me.tomassetti.symbolsolver.reflectionmodel;

import com.github.javaparser.ast.Node;
import javaslang.Tuple2;
import me.tomassetti.symbolsolver.logic.AbstractTypeDeclaration;
import me.tomassetti.symbolsolver.logic.GenericTypeInferenceLogic;
import me.tomassetti.symbolsolver.resolution.MethodResolutionLogic;
import me.tomassetti.symbolsolver.model.declarations.*;
import me.tomassetti.symbolsolver.model.invokations.MethodUsage;
import me.tomassetti.symbolsolver.core.resolution.Context;
import me.tomassetti.symbolsolver.model.resolution.SymbolReference;
import me.tomassetti.symbolsolver.model.resolution.TypeParameter;
import me.tomassetti.symbolsolver.model.resolution.TypeSolver;
import me.tomassetti.symbolsolver.model.typesystem.NullType;
import me.tomassetti.symbolsolver.model.typesystem.ReferenceType;
import me.tomassetti.symbolsolver.model.typesystem.ReferenceTypeImpl;
import me.tomassetti.symbolsolver.model.typesystem.Type;
import me.tomassetti.symbolsolver.javaparsermodel.LambdaArgumentTypePlaceholder;
import me.tomassetti.symbolsolver.javaparsermodel.UnsolvedSymbolException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ReflectionInterfaceDeclaration extends AbstractTypeDeclaration implements InterfaceDeclaration {

    private Class<?> clazz;
    private TypeSolver typeSolver;

    public ReflectionInterfaceDeclaration(Class<?> clazz, TypeSolver typeSolver) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException();
        }

        this.clazz = clazz;
        this.typeSolver = typeSolver;
    }

    @Override
    public boolean isAssignableBy(TypeDeclaration other) {
        return isAssignableBy(new ReferenceTypeImpl(other, typeSolver));
    }

    @Override
    public String getQualifiedName() {
        return clazz.getCanonicalName();
    }

    public Context getContext() {
        return new ClassOrInterfaceDeclarationContext(clazz);
    }

    @Override
    public SymbolReference<MethodDeclaration> solveMethod(String name, List<Type> parameterTypes) {
        List<MethodDeclaration> methods = new ArrayList<>();
        for (Method method : clazz.getMethods()) {
            if (method.isBridge() || method.isSynthetic()) continue;
            MethodDeclaration methodDeclaration = new ReflectionMethodDeclaration(method, typeSolver);
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

    public Type getUsage(Node node) {
        return new ReferenceTypeImpl(this, typeSolver);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReflectionInterfaceDeclaration)) return false;

        ReflectionInterfaceDeclaration that = (ReflectionInterfaceDeclaration) o;

        if (!clazz.getCanonicalName().equals(that.clazz.getCanonicalName())) return false;

        if (!getTypeParameters().equals(that.getTypeParameters())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    public Optional<MethodUsage> solveMethodAsUsage(String name, List<Type> parameterTypes, TypeSolver typeSolver, Context invokationContext, List<Type> typeParameterValues) {
        Optional<MethodUsage> res =  ReflectionMethodResolutionLogic.solveMethodAsUsage(name, parameterTypes, typeSolver, invokationContext,
                typeParameterValues, this, clazz);
        if (res.isPresent()) {
            // We have to replace method type parameters here
            List<Tuple2<Type, Type>> formalActualTypePairs = new ArrayList<>();
            MethodUsage methodUsage = res.get();
            int i=0;
            for (Type actualType : parameterTypes) {
                Type formalType = methodUsage.getParamType(i, typeSolver);
                // We need to replace the class type parameters (while we derive the method ones)

                formalActualTypePairs.add(new Tuple2<>(formalType, actualType));
                i++;
            }
            Map<String, Type> map = GenericTypeInferenceLogic.inferGenericTypes(formalActualTypePairs);
            for (String key : map.keySet()) {
                if (map.get(key) == null) {
                    throw new IllegalArgumentException();
                }
                methodUsage = methodUsage.replaceNameParam(key, map.get(key));
            }
            return Optional.of(methodUsage);
        } else {
            return res;
        }
    }

    @Override
    public boolean canBeAssignedTo(TypeDeclaration other) {
        if (other instanceof LambdaArgumentTypePlaceholder) {
            return getQualifiedName().equals(Predicate.class.getCanonicalName()) ||
                    getQualifiedName().equals(Function.class.getCanonicalName());
        }
        if (other.getQualifiedName().equals(getQualifiedName())) {
            return true;
        }
        if (this.clazz.getSuperclass() != null
                && new ReflectionInterfaceDeclaration(clazz.getSuperclass(), typeSolver).canBeAssignedTo(other)) {
                return true;
        }
        for (Class interfaze : clazz.getInterfaces()) {
            if (new ReflectionInterfaceDeclaration(interfaze, typeSolver).canBeAssignedTo(other)) {
                return true;
            }
        }

        if (other.getQualifiedName().equals(Object.class.getCanonicalName())) {
            return true;
        }

        return false;
    }

    @Override
    public boolean isAssignableBy(Type type) {
        if (type instanceof NullType) {
            return true;
        }
        if (type instanceof LambdaArgumentTypePlaceholder) {
            return getQualifiedName().equals(Predicate.class.getCanonicalName()) ||
                    getQualifiedName().equals(Function.class.getCanonicalName());
        }
        if (type.isArray()) {
            return false;
        }
        if (type.isPrimitive()) {
            return false;
        }
        if (type.describe().equals(getQualifiedName())) {
            return true;
        }
        if (type instanceof ReferenceTypeImpl) {
            ReferenceTypeImpl otherTypeDeclaration = (ReferenceTypeImpl) type;
            return otherTypeDeclaration.getTypeDeclaration().canBeAssignedTo(this);
        }

        return false;
    }

    @Override
    public boolean isTypeVariable() {
        return false;
    }

    @Override
    public FieldDeclaration getField(String name) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return new ReflectionFieldDeclaration(field, typeSolver);
            }
        }
        for (ReferenceType ancestor : getAllAncestors()) {
            if (ancestor.getTypeDeclaration().hasField(name)) {
                return ancestor.getTypeDeclaration().getField(name);
            }
        }
        throw new UnsolvedSymbolException("Field in " + this, name);
    }

    @Override
    public List<FieldDeclaration> getAllFields() {
        List<FieldDeclaration> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            fields.add(new ReflectionFieldDeclaration(field, typeSolver));
        }
        for (ReferenceType ancestor : getAllAncestors()) {
            fields.addAll(ancestor.getTypeDeclaration().getAllFields());
        }
        return fields;
    }
    
    /*@Override
    public boolean canBeAssignedTo(TypeDeclaration other, TypeSolver typeSolver) {
        if (getQualifiedName().equals(other.getQualifiedName())) {
            return true;
        }
        if (clazz.getSuperclass() != null) {
            if (new ReflectionClassDeclaration(clazz.getSuperclass()).isAssignableBy(other, typeSolver)){
                return true;
            }
        }
        for (Class<?> interfaze : clazz.getInterfaces()) {
            if (new ReflectionClassDeclaration(interfaze).isAssignableBy(other, typeSolver)){
                return true;
            }
        }
        return false;
    }*/

    @Override
    public SymbolReference<? extends ValueDeclaration> solveSymbol(String name, TypeSolver typeSolver) {
        for (Field field : clazz.getFields()) {
            if (field.getName().equals(name)) {
                return SymbolReference.solved(new ReflectionFieldDeclaration(field, typeSolver));
            }
        }
        return SymbolReference.unsolved(ValueDeclaration.class);
    }

    @Override
    public SymbolReference<TypeDeclaration> solveType(String substring, TypeSolver typeSolver) {
        return SymbolReference.unsolved(TypeDeclaration.class);
    }

    @Override
    public List<ReferenceType> getAncestors() {
        List<ReferenceType> ancestors = new LinkedList<>();
        if (clazz.getSuperclass() != null) {
            ReferenceTypeImpl superclass = new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(clazz.getSuperclass(), typeSolver), typeSolver);
            ancestors.add(superclass);
        }
        for (Class<?> interfaze : clazz.getInterfaces()) {
            ReferenceTypeImpl interfazeDecl = new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(interfaze, typeSolver), typeSolver);
            ancestors.add(interfazeDecl);
        }
        for (int i = 0; i < ancestors.size(); i++) {
            if (ancestors.get(i).getQualifiedName().equals(Object.class.getCanonicalName())) {
                ancestors.remove(i);
                i--;
            }
        }
        ReferenceTypeImpl object = new ReferenceTypeImpl(new ReflectionClassDeclaration(Object.class, typeSolver), typeSolver);
        ancestors.add(object);
        return ancestors;
    }

    @Override
    public Set<MethodDeclaration> getDeclaredMethods() {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> new ReflectionMethodDeclaration(m, typeSolver()))
                .collect(Collectors.toSet());
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
    public boolean isInterface() {
        return true;
    }

    @Override
    public List<InterfaceDeclaration> getInterfacesExtended() {
        List<InterfaceDeclaration> res = new ArrayList<>();
        for (Class i : clazz.getInterfaces()) {
            res.add(new ReflectionInterfaceDeclaration(i, typeSolver));
        }
        return res;
    }

    @Override
    public InterfaceDeclaration asInterface() {
        return this;
    }

    @Override
    public boolean hasDirectlyAnnotation(String canonicalName) {
        for (Annotation a : clazz.getDeclaredAnnotations()) {
            if (a.annotationType().getCanonicalName().equals(canonicalName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<TypeParameter> getTypeParameters() {
        List<TypeParameter> params = new ArrayList<>();
        for (TypeVariable tv : this.clazz.getTypeParameters()) {
            params.add(new ReflectionTypeParameter(tv, true));
        }
        return params;
    }

    @Override
    protected TypeSolver typeSolver() {
        return typeSolver;
    }
}

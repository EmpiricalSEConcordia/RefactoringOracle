package me.tomassetti.symbolsolver.model.reflection;

import com.github.javaparser.ast.Node;
import me.tomassetti.symbolsolver.model.Context;
import me.tomassetti.symbolsolver.model.TypeParameter;
import me.tomassetti.symbolsolver.model.TypeSolver;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;
import me.tomassetti.symbolsolver.model.declarations.ParameterDeclaration;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;
import me.tomassetti.symbolsolver.model.usages.MethodUsage;
import me.tomassetti.symbolsolver.model.usages.TypeUsage;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Created by federico on 02/08/15.
 */
public class ReflectionMethodDeclaration implements MethodDeclaration {

    private Method method;

    public ReflectionMethodDeclaration(Method method) {
        this.method = method;
    }

    @Override
    public String getName() {
        return method.getName();
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
    public boolean isVariable() {
        return false;
    }

    @Override
    public String toString() {
        return "ReflectionMethodDeclaration{" +
                "method=" + method +
                '}';
    }

    @Override
    public boolean isType() {
        return false;
    }

    @Override
    public TypeDeclaration declaringType() {
        if (method.getDeclaringClass().isInterface()) {
            return new ReflectionInterfaceDeclaration(method.getDeclaringClass());
        } else {
            return new ReflectionClassDeclaration(method.getDeclaringClass());
        }
    }

    @Override
    public TypeUsage getReturnType(TypeSolver typeSolver) {
        return ReflectionFactory.typeUsageFor(method.getGenericReturnType());
    }

    @Override
    public int getNoParams() {
        return method.getParameterTypes().length;
    }

    @Override
    public ParameterDeclaration getParam(int i) {
        return new ReflectionParameterDeclaration(method.getParameterTypes()[i], method.getGenericParameterTypes()[i]);
    }

    @Override
    public MethodUsage getUsage(Node node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TypeParameter> getTypeParameters() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MethodUsage resolveTypeVariables(Context context, TypeSolver typeSolver) {
        return new MethodUsage(new ReflectionMethodDeclaration(method), typeSolver);
    }

    @Override
    public Context getContext() {
        throw new UnsupportedOperationException();
    }
}

package me.tomassetti.symbolsolver.model.reflection;

import com.github.javaparser.ast.Node;
import me.tomassetti.symbolsolver.model.TypeParameter;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;
import me.tomassetti.symbolsolver.model.declarations.ParameterDeclaration;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;
import me.tomassetti.symbolsolver.model.usages.MethodUsage;

import java.lang.reflect.Method;
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
    public String toString() {
        return "ReflectionMethodDeclaration{" +
                "method=" + method +
                '}';
    }

    @Override
    public boolean isType() {
        return false;
    }

    /*@Override
    public TypeDeclaration asTypeDeclaration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeDeclaration getType() {
        throw new UnsupportedOperationException();
    }*/

    @Override
    public TypeDeclaration declaringType() {
        return new ReflectionClassDeclaration(method.getDeclaringClass());
    }

    @Override
    public TypeDeclaration getReturnType() {
        return new ReflectionClassDeclaration(method.getReturnType());
    }

    @Override
    public int getNoParams() {
        return method.getParameterTypes().length;
    }

    @Override
    public ParameterDeclaration getParam(int i) {
        return new ReflectionParameterDeclaration(method.getParameterTypes()[i]);
    }

    @Override
    public MethodUsage getUsage(Node node) {
        return null;
    }

    @Override
    public List<TypeParameter> getTypeParameters() {
        return null;
    }
}

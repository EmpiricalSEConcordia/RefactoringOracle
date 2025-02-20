package me.tomassetti.symbolsolver.model.javassist;

import javassist.CtClass;
import me.tomassetti.symbolsolver.model.declarations.ParameterDeclaration;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;

/**
 * Created by federico on 02/08/15.
 */
public class JavassistParameterDeclaration implements ParameterDeclaration {
    @Override
    public String toString() {
        return "JavassistParameterDeclaration{" +
                "type=" + type +
                '}';
    }

    private CtClass type;

    public JavassistParameterDeclaration(CtClass type) {
        this.type = type;
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isField() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isParameter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeDeclaration asTypeDeclaration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeDeclaration getType() {
        return new JavassistClassDeclaration(type);
    }
}

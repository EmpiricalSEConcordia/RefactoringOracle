package me.tomassetti.symbolsolver.model.resolution;

import me.tomassetti.symbolsolver.model.declarations.ValueDeclaration;
import me.tomassetti.symbolsolver.model.typesystem.Type;

/**
 * @author Federico Tomassetti
 */
public class Value {
    @Override
    public String toString() {
        return "Value{" +
                "typeUsage=" + type +
                ", name='" + name + '\'' +
                ", field=" + field +
                '}';
    }

    private Type type;
    private String name;
    private boolean field;

    public Value(Type type, String name, boolean field) {
        this.type = type;
        this.name = name;
        this.field = field;
    }

    public static Value from(ValueDeclaration decl, TypeSolver typeSolver) {
        Type type = decl.getType();
        return new Value(type, decl.getName(), decl.isField());
    }

    public String getName() {
        return name;
    }

    public boolean isField() {
        return field;
    }

    public Type getUsage() {
        return type;
    }

}

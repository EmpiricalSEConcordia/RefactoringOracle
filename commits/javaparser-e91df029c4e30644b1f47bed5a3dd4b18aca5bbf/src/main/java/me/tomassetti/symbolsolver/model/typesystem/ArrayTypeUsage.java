package me.tomassetti.symbolsolver.model.typesystem;

public class ArrayTypeUsage implements TypeUsage {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayTypeUsage that = (ArrayTypeUsage) o;

        if (!baseType.equals(that.baseType)) return false;

        return true;
    }

    @Override
    public String toString() {
        return "ArrayTypeUsage{" +
                "baseType=" + baseType +
                '}';
    }

    @Override
    public ArrayTypeUsage asArrayTypeUsage() {
        return this;
    }

    @Override
    public int hashCode() {
        return baseType.hashCode();
    }

    private TypeUsage baseType;

    public ArrayTypeUsage(TypeUsage baseType) {
        this.baseType = baseType;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public String describe() {
        return baseType.describe()+"[]";
    }

    public TypeUsage getComponentType() {
        return baseType;
    }

    @Override
    public TypeUsage replaceParam(String name, TypeUsage replaced) {
        TypeUsage baseTypeReplaced = baseType.replaceParam(name, replaced);
        if (baseTypeReplaced == baseType) {
            return this;
        } else {
            return new ArrayTypeUsage(baseTypeReplaced);
        }
    }

    @Override
    public boolean isAssignableBy(TypeUsage other) {
        if (other instanceof ArrayTypeUsage) {
            return baseType.equals(((ArrayTypeUsage) other).getComponentType());
        } else {
            return false;
        }
    }

}

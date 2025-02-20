package com.github.javaparser.ast.nodeTypes;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclaratorId;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

public interface NodeWithParameters<N extends Node> {
    NodeList<Parameter> getParameters();

    N setParameters(NodeList<Parameter> parameters);

    default N addParameter(Type type, String name) {
        return addParameter(new Parameter(type, new VariableDeclaratorId(name)));
    }

    default N addParameter(Class<?> paramClass, String name) {
        ((Node) this).tryAddImportToParentCompilationUnit(paramClass);
        return addParameter(new ClassOrInterfaceType(paramClass.getSimpleName()), name);
    }

    /**
     * Remember to import the class in the compilation unit yourself
     * 
     * @param className the name of the class, ex : org.test.Foo or Foo if you added manually the import
     * @param name the name of the parameter
     */
    default N addParameter(String className, String name) {
        return addParameter(new ClassOrInterfaceType(className), name);
    }

    @SuppressWarnings("unchecked")
    default N addParameter(Parameter parameter) {
        getParameters().add(parameter);
        return (N) this;
    }

    default Parameter addAndGetParameter(Type type, String name) {
        return addAndGetParameter(new Parameter(type, new VariableDeclaratorId(name)));
    }

    default Parameter addAndGetParameter(Class<?> paramClass, String name) {
        ((Node) this).tryAddImportToParentCompilationUnit(paramClass);
        return addAndGetParameter(new ClassOrInterfaceType(paramClass.getSimpleName()), name);
    }

    /**
     * Remember to import the class in the compilation unit yourself
     * 
     * @param className the name of the class, ex : org.test.Foo or Foo if you added manually the import
     * @param name the name of the parameter
     * @return the {@link Parameter} created
     */
    default Parameter addAndGetParameter(String className, String name) {
        return addAndGetParameter(new ClassOrInterfaceType(className), name);
    }

    default Parameter addAndGetParameter(Parameter parameter) {
        getParameters().add(parameter);
        return parameter;
    }

    /**
     * Try to find a {@link Parameter} by its name
     * 
     * @param name the name of the param
     * @return null if not found, the param found otherwise
     */
    default Parameter getParamByName(String name) {
        return getParameters().stream()
                .filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * Try to find a {@link Parameter} by its type
     * 
     * @param type the type of the param
     * @return null if not found, the param found otherwise
     */
    default Parameter getParamByType(String type) {
        return getParameters().stream()
                .filter(p -> p.getType().toString().equals(type)).findFirst().orElse(null);
    }

    /**
     * Try to find a {@link Parameter} by its type
     * 
     * @param type the type of the param <b>take care about generics, it wont work</b>
     * @return null if not found, the param found otherwise
     */
    default Parameter getParamByType(Class<?> type) {
        return getParameters().stream()
                .filter(p -> p.getType().toString().equals(type.getSimpleName())).findFirst().orElse(null);
    }
}

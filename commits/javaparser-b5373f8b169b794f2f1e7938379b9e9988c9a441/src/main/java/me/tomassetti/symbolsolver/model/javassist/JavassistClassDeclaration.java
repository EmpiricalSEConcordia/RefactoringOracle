package me.tomassetti.symbolsolver.model.javassist;

import com.github.javaparser.ast.Node;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;
import me.tomassetti.symbolsolver.model.*;
import me.tomassetti.symbolsolver.model.declarations.ClassDeclaration;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;
import me.tomassetti.symbolsolver.model.javassist.contexts.JavassistMethodContext;
import me.tomassetti.symbolsolver.model.usages.MethodUsage;
import me.tomassetti.symbolsolver.model.usages.TypeUsageOfTypeDeclaration;
import me.tomassetti.symbolsolver.model.usages.TypeUsage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by federico on 01/08/15.
 */
public class JavassistClassDeclaration implements ClassDeclaration {

    private CtClass ctClass;

    public JavassistClassDeclaration(CtClass ctClass) {
        if (ctClass == null) {
            throw new IllegalArgumentException();
        }
        this.ctClass = ctClass;
    }

    @Override
    public String getQualifiedName() {
        return ctClass.getName();
    }

    private List<TypeUsage> parseTypeParameters(String signature, TypeSolver typeSolver, Context context) {
        String originalSignature = signature;
        if (signature.contains("<")) {
            signature = signature.substring(signature.indexOf('<') + 1);
            if (!signature.endsWith(">")) {
                throw new IllegalArgumentException();
            }
            signature = signature.substring(0, signature.length() - 1);
            if (signature.contains(",")){
                throw new UnsupportedOperationException();
            }
            if (signature.contains("<")){
                throw new UnsupportedOperationException(originalSignature);
            }
            if (signature.contains(">")){
                throw new UnsupportedOperationException();
            }
            List<TypeUsage> typeUsages = new ArrayList<>();
            typeUsages.add(new SymbolSolver(typeSolver).solveTypeUsage(signature, context));
            return typeUsages;
        } else {
            return Collections.emptyList();
        }
    }


    @Override
    public Optional<MethodUsage> solveMethodAsUsage(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver) {

        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (method.getName().equals(name)){
                // TODO check parameters
                MethodUsage methodUsage = new MethodUsage(new JavassistMethodDeclaration(method, typeSolver), typeSolver);
                try {
                    SignatureAttribute.MethodSignature classSignature = SignatureAttribute.toMethodSignature(method.getGenericSignature());
                    List<TypeUsage> parametersOfReturnType = parseTypeParameters(classSignature.getReturnType().toString(), typeSolver, new JavassistMethodContext(method));
                    TypeUsage newReturnType = methodUsage.returnType();
                    for (int i=0;i<parametersOfReturnType.size();i++) {
                        newReturnType = newReturnType.replaceParam(i, parametersOfReturnType.get(i));
                    }
                    methodUsage = methodUsage.replaceReturnType(newReturnType);
                    return Optional.of(methodUsage);
                } catch (BadBytecode e){
                    throw new RuntimeException(e);
                }
            }
        }

        try {
            CtClass superClass = ctClass.getSuperclass();
            if (superClass != null) {
                Optional<MethodUsage> ref = new JavassistClassDeclaration(superClass).solveMethodAsUsage(name, parameterTypes, typeSolver);
                if (ref.isPresent()) {
                    return ref;
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            for (CtClass interfaze : ctClass.getInterfaces()) {
                Optional<MethodUsage> ref = new JavassistClassDeclaration(interfaze).solveMethodAsUsage(name, parameterTypes, typeSolver);
                if (ref.isPresent()) {
                    return ref;
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    @Override
    public Context getContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SymbolReference<MethodDeclaration> solveMethod(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver) {

        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (method.getName().equals(name)){
                // TODO check parameters
                return SymbolReference.solved(new JavassistMethodDeclaration(method, typeSolver));
            }
        }

        try {
            CtClass superClass = ctClass.getSuperclass();
            if (superClass != null) {
                SymbolReference<MethodDeclaration> ref = new JavassistClassDeclaration(superClass).solveMethod(name, parameterTypes, typeSolver);
                if (ref.isSolved()) {
                    return ref;
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            for (CtClass interfaze : ctClass.getInterfaces()) {
                SymbolReference<MethodDeclaration> ref = new JavassistClassDeclaration(interfaze).solveMethod(name, parameterTypes, typeSolver);
                if (ref.isSolved()) {
                    return ref;
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }

        return SymbolReference.unsolved(MethodDeclaration.class);
    }

    @Override
    public TypeUsage getUsage(Node node) {
        return new TypeUsageOfTypeDeclaration(this);
    }

    @Override
    public boolean isAssignableBy(TypeUsage typeUsage) {
        throw new UnsupportedOperationException();
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
    public boolean hasField(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return ctClass.getSimpleName();
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
    public String toString() {
        return "JavassistClassDeclaration }" + ctClass.getName() + '}';
    }

    @Override
    public List<TypeParameter> getTypeParameters() {
        if (null == ctClass.getGenericSignature()) {
            return Collections.emptyList();
        } else {
            try {
                SignatureAttribute.ClassSignature classSignature = SignatureAttribute.toClassSignature(ctClass.getGenericSignature());
                return Arrays.<SignatureAttribute.TypeParameter>stream(classSignature.getParameters()).map((tp)->new JavassistTypeParameter(tp, true)).collect(Collectors.toList());
            } catch (BadBytecode badBytecode) {
                throw new RuntimeException(badBytecode);
            }
        }
    }
}

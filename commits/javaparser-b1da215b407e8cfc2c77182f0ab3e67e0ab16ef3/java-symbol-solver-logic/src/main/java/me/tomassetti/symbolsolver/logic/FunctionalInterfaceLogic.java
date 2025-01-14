/*
 * Copyright 2016 Federico Tomassetti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.tomassetti.symbolsolver.logic;

import me.tomassetti.symbolsolver.model.usages.MethodUsage;
import me.tomassetti.symbolsolver.model.usages.typesystem.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FunctionalInterfaceLogic {

    public static Optional<MethodUsage> getFunctionalMethod(Type type) {
        if (type.isReferenceType() && type.asReferenceType().getTypeDeclaration().isInterface()) {
            //We need to find all abstract methods
            Set<MethodUsage> allMethods = type.asReferenceType().getTypeDeclaration().getAllMethods();
            Set<MethodUsage> methods = type.asReferenceType().getTypeDeclaration().getAllMethods().stream()
                    .filter(m -> m.getDeclaration().isAbstract())
                    // Remove methods inherited by Object:
                    // Consider the case of Comparator which define equals. It would be considered a functional method.
                    .filter(m -> !declaredOnObject(m))
                    .collect(Collectors.toSet());


            if (methods.size() == 1) {
                return Optional.of(methods.iterator().next());
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    private static String getSignature(Method m) {
        return String.format("%s(%s)", m.getName(), String.join(", ", Arrays.stream(m.getParameters()).map(p -> toSignature(p)).collect(Collectors.toList())));
    }

    private static String toSignature(Parameter p) {
        return p.getType().getCanonicalName();
    }

    private static List<String> OBJECT_METHODS = Arrays.stream(Object.class.getDeclaredMethods())
            .map(method -> getSignature(method))
            .collect(Collectors.toList());

    private static boolean declaredOnObject(MethodUsage m) {
        return OBJECT_METHODS.contains(m.getDeclaration().getSignature());
    }
}

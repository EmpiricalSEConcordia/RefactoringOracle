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

package com.github.javaparser.symbolsolver.model.declarations;

/**
 * @author Federico Tomassetti
 */
public interface MethodLikeDeclaration extends Declaration, TypeParametrizable, HasAccessLevel {

    default String getQualifiedName() {
        return declaringType().getQualifiedName() + "." + this.getName();
    }

    default String getSignature() {
        StringBuffer sb = new StringBuffer();
        sb.append(getName());
        sb.append("(");
        for (int i = 0; i < getNoParams(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(getParam(i).describeType());
        }
        sb.append(")");
        return sb.toString();
    }

    default String getQualifiedSignature() {
        return declaringType().getQualifiedName() + "." + this.getSignature();
    }

    /**
     * The type in which the method is declared.
     */
    TypeDeclaration declaringType();

    int getNoParams();

    ParameterDeclaration getParam(int i);

    /**
     * The last parameter can be variadic and sometimes it needs to be handled in a special way.
     */
    default ParameterDeclaration getLastParam() {
        if (getNoParams() == 0) {
            throw new UnsupportedOperationException("This method has no typeParametersValues, therefore it has no a last parameter");
        }
        return getParam(getNoParams() - 1);
    }

    /**
     * Note that when a method has a variadic parameter it should have an array type.
     *
     * @return
     */
    default boolean hasVariadicParameter() {
        if (getNoParams() == 0) {
            return false;
        } else {
            return getParam(getNoParams() - 1).isVariadic();
        }
    }
}

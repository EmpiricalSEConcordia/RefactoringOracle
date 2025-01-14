/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.expression;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;

/**
 * EL property accessor that knows how to traverse the beans of a
 * Spring {@link org.springframework.beans.factory.BeanFactory}.
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
public class BeanFactoryAccessor implements PropertyAccessor {

	public boolean canRead(EvaluationContext context, Object target, Object name) throws AccessException {
		return (((BeanFactory) target).containsBean(name.toString()));
	}

	public Object read(EvaluationContext context, Object target, Object name) throws AccessException {
		return ((BeanFactory) target).getBean(name.toString());
	}

	public boolean canWrite(EvaluationContext context, Object target, Object name) throws AccessException {
		return false;
	}

	public void write(EvaluationContext context, Object target, Object name, Object newValue) throws AccessException {
		throw new AccessException("Beans in a BeanFactory are read-only");
	}

	public Class[] getSpecificTargetClasses() {
		return new Class[] {BeanFactory.class};
	}

}

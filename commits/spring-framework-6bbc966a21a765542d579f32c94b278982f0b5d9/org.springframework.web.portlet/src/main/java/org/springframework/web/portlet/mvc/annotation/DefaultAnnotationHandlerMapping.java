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

package org.springframework.web.portlet.mvc.annotation;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import javax.portlet.PortletMode;
import javax.portlet.PortletRequest;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.generic.GenericBeanFactoryAccessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.portlet.handler.AbstractMapBasedHandlerMapping;

/**
 * Implementation of the {@link org.springframework.web.portlet.HandlerMapping}
 * interface that maps handlers based on portlet modes expressed through the
 * {@link RequestMapping} annotation at the type or method level.
 *
 * <p>Registered by default in {@link org.springframework.web.portlet.DispatcherPortlet}
 * on Java 5+. <b>NOTE:</b> If you define custom HandlerMapping beans in your
 * DispatcherPortlet context, you need to add a DefaultAnnotationHandlerMapping bean
 * explicitly, since custom HandlerMapping beans replace the default mapping strategies.
 * Defining a DefaultAnnotationHandlerMapping also allows for registering custom
 * interceptors:
 *
 * <pre class="code">
 * &lt;bean class="org.springframework.web.portlet.mvc.annotation.DefaultAnnotationHandlerMapping"&gt;
 *   &lt;property name="interceptors"&gt;
 *     ...
 *   &lt;/property&gt;
 * &lt;/bean&gt;</pre>
 *
 * Annotated controllers are usually marked with the {@link Controller} stereotype
 * at the type level. This is not strictly necessary when {@link RequestMapping} is
 * applied at the type level (since such a handler usually implements the
 * {@link org.springframework.web.portlet.mvc.Controller} interface). However,
 * {@link Controller} is required for detecting {@link RequestMapping} annotations
 * at the method level.
 *
 * <p><b>NOTE:</b> Method-level mappings are only allowed to narrow the mapping
 * expressed at the class level (if any). Portlet modes need to uniquely map onto
 * specific handler beans, with any given portlet mode only allowed to be mapped
 * onto one specific handler bean (not spread across multiple handler beans).
 * It is strongly recommended to co-locate related handler methods into the same bean.
 *
 * <p>The {@link AnnotationMethodHandlerAdapter} is responsible for processing
 * annotated handler methods, as mapped by this HandlerMapping. For
 * {@link RequestMapping} at the type level, specific HandlerAdapters such as
 * {@link org.springframework.web.portlet.mvc.SimpleControllerHandlerAdapter} apply.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see RequestMapping
 * @see AnnotationMethodHandlerAdapter
 */
public class DefaultAnnotationHandlerMapping extends AbstractMapBasedHandlerMapping {

	/**
	 * Calls the <code>registerHandlers</code> method in addition
	 * to the superclass's initialization.
	 * @see #detectHandlers
	 */
	@Override
	public void initApplicationContext() throws BeansException {
		super.initApplicationContext();
		detectHandlers();
	}

	/**
	 * Register all handlers specified in the Portlet mode map for the corresponding modes.
	 * @throws org.springframework.beans.BeansException if the handler couldn't be registered
	 */
	protected void detectHandlers() throws BeansException {
		ApplicationContext context = getApplicationContext();
		String[] beanNames = context.getBeanNamesForType(Object.class);
		for (String beanName : beanNames) {
			Class<?> handlerType = context.getType(beanName);
			ListableBeanFactory bf = (context instanceof ConfigurableApplicationContext ?
					((ConfigurableApplicationContext) context).getBeanFactory() : context);
			GenericBeanFactoryAccessor bfa = new GenericBeanFactoryAccessor(bf);
			RequestMapping mapping = bfa.findAnnotationOnBean(beanName, RequestMapping.class);
			if (mapping != null) {
				String[] modeKeys = mapping.value();
				String[] params = mapping.params();
				boolean registerHandlerType = true;
				if (modeKeys.length == 0 || params.length == 0) {
					registerHandlerType = !detectHandlerMethods(handlerType, beanName, mapping);
				}
				if (registerHandlerType) {
					ParameterMappingPredicate predicate = new ParameterMappingPredicate(params);
					for (String modeKey : modeKeys) {
						registerHandler(new PortletMode(modeKey), beanName, predicate);
					}
				}
			}
			else if (AnnotationUtils.findAnnotation(handlerType, Controller.class) != null) {
				detectHandlerMethods(handlerType, beanName, mapping);
			}
		}
	}

	/**
	 * Derive portlet mode mappings from the handler's method-level mappings.
	 * @param handlerType the handler type to introspect
	 * @param beanName the name of the bean introspected
	 * @param typeMapping the type level mapping (if any)
	 * @return <code>true</code> if at least 1 handler method has been registered;
	 * <code>false</code> otherwise
	 */
	protected boolean detectHandlerMethods(Class handlerType, final String beanName, final RequestMapping typeMapping) {
		final Set<Boolean> handlersRegistered = new HashSet<Boolean>(1);
		ReflectionUtils.doWithMethods(handlerType, new ReflectionUtils.MethodCallback() {
			public void doWith(Method method) {
				RequestMapping mapping = method.getAnnotation(RequestMapping.class);
				if (mapping != null) {
					String[] modeKeys = mapping.value();
					if (modeKeys.length == 0) {
						if (typeMapping != null) {
							modeKeys = typeMapping.value();
						}
						else {
							throw new IllegalStateException(
									"No portlet mode mappings specified - neither at type nor method level");
						}
					}
					String[] params = mapping.params();
					if (typeMapping != null) {
						PortletAnnotationMappingUtils.validateModeMapping(modeKeys, typeMapping.value());
						params = StringUtils.mergeStringArrays(typeMapping.params(), params);
					}
					ParameterMappingPredicate predicate = new ParameterMappingPredicate(params);
					for (String modeKey : modeKeys) {
						registerHandler(new PortletMode(modeKey), beanName, predicate);
						handlersRegistered.add(Boolean.TRUE);
					}
				}
			}
		});
		return !handlersRegistered.isEmpty();
	}

	/**
	 * Uses the current PortletMode as lookup key.
	 */
	@Override
	protected Object getLookupKey(PortletRequest request) throws Exception {
		return request.getPortletMode();
	}


	/**
	 * Predicate that matches against parameter conditions.
	 */
	private static class ParameterMappingPredicate implements PortletRequestMappingPredicate {

		private final String[] params;

		private ParameterMappingPredicate(String[] params) {
			this.params = params;
		}

		public boolean match(PortletRequest request) {
			return PortletAnnotationMappingUtils.checkParameters(this.params, request);
		}

		public int compareTo(Object other) {
			if (other instanceof PortletRequestMappingPredicate) {
				return new Integer(((ParameterMappingPredicate) other).params.length).compareTo(this.params.length);
			}
			else {
				return 0;
			}
		}

		@Override
		public String toString() {
			return StringUtils.arrayToCommaDelimitedString(this.params);
		}
	}

}

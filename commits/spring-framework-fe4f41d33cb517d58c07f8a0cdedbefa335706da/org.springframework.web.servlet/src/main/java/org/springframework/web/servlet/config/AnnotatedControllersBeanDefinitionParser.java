/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.web.servlet.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping;
import org.w3c.dom.Element;

/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser} that parses the {@code annotated-controllers} element to setup
 * <code>@Controller</code> configuration in a Spring MVC web application.
 * 
 * @author Keith Donald
 */
public class AnnotatedControllersBeanDefinitionParser implements BeanDefinitionParser {

	public BeanDefinition parse(Element element, ParserContext parserContext) {
		Object source = parserContext.extractSource(element);
		BeanDefinitionHolder handlerMappingHolder = registerDefaultAnnotationHandlerMapping(element, source, parserContext);
		BeanDefinitionHolder handlerAdapterHolder = registerAnnotationMethodHandlerAdapter(element, source, parserContext);
		
		CompositeComponentDefinition compDefinition = new CompositeComponentDefinition(element.getTagName(), source);
		parserContext.pushContainingComponent(compDefinition);
		parserContext.registerComponent(new BeanComponentDefinition(handlerMappingHolder));
		parserContext.registerComponent(new BeanComponentDefinition(handlerAdapterHolder));
		parserContext.popAndRegisterContainingComponent();
		
		return null;
	}
	
	private BeanDefinitionHolder registerDefaultAnnotationHandlerMapping(Element element, Object source, ParserContext context) {
		BeanDefinitionBuilder builder = createBeanBuilder(DefaultAnnotationHandlerMapping.class, source);
		builder.addPropertyValue("order", 0);
		return registerBeanDefinition(new BeanDefinitionHolder(builder.getBeanDefinition(), "defaultAnnotationHandlerMapping"), context);
	}

	private BeanDefinitionHolder registerAnnotationMethodHandlerAdapter(Element element, Object source, ParserContext context) {
		BeanDefinitionBuilder builder = createBeanBuilder(AnnotationMethodHandlerAdapter.class, source);
		builder.addPropertyValue("webBindingInitializer", createWebBindingInitializer(element, source, context));
		return registerBeanDefinition(new BeanDefinitionHolder(builder.getBeanDefinition(), "annotationMethodHandlerAdapter"), context);	
	}

	private BeanDefinition createWebBindingInitializer(Element element, Object source, ParserContext context) {
		BeanDefinitionBuilder builder = createBeanBuilder(ConfigurableWebBindingInitializer.class, source);
		if (context.getRegistry().containsBeanDefinition("conversionService")) {
			builder.addPropertyReference("conversionService", "conversionService");
		} else {
			builder.addPropertyValue("conversionService", createFormattingConversionService(element, source, context));
		}
		return builder.getBeanDefinition();
	}
	
	private BeanDefinition createFormattingConversionService(Element element, Object source, ParserContext context) {
		BeanDefinitionBuilder builder = createBeanBuilder(FormattingConversionServiceFactoryBean.class, source);
		return builder.getBeanDefinition();
	}

	private BeanDefinitionHolder registerBeanDefinition(BeanDefinitionHolder holder, ParserContext context) {
		context.getRegistry().registerBeanDefinition(holder.getBeanName(), holder.getBeanDefinition());
		return holder;
	}
	
	private BeanDefinitionBuilder createBeanBuilder(Class<?> clazz, Object source) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
		builder.getRawBeanDefinition().setSource(source);
		return builder;
	}

}

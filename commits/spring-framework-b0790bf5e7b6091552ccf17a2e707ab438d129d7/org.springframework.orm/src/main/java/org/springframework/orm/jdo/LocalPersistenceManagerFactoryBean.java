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

package org.springframework.orm.jdo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jdo.JDOException;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.util.CollectionUtils;

/**
 * {@link org.springframework.beans.factory.FactoryBean} that creates a
 * JDO {@link javax.jdo.PersistenceManagerFactory}. This is the usual way to
 * set up a shared JDO PersistenceManagerFactory in a Spring application context;
 * the PersistenceManagerFactory can then be passed to JDO-based DAOs via
 * dependency injection. Note that switching to a JNDI lookup or to a bean-style
 * PersistenceManagerFactory instance is just a matter of configuration!
 *
 * <p>Configuration settings can either be read from a properties file,
 * specified as "configLocation", or locally specified. Properties
 * specified as "jdoProperties" here will override any settings in a file.
 * On JDO 2.1, you may alternatively specify a "persistenceManagerFactoryName",
 * referring to a PMF definition in "META-INF/jdoconfig.xml"
 * (see {@link #setPersistenceManagerFactoryName}).
 *
 * <p><b>NOTE: This class requires JDO 2.0 or higher, as of Spring 2.5.</b>
 *
 * <p>This class also implements the
 * {@link org.springframework.dao.support.PersistenceExceptionTranslator}
 * interface, as autodetected by Spring's
 * {@link org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor},
 * for AOP-based translation of native exceptions to Spring DataAccessExceptions.
 * Hence, the presence of a LocalPersistenceManagerFactoryBean automatically enables
 * a PersistenceExceptionTranslationPostProcessor to translate JDO exceptions.
 *
 * <p><b>Alternative: Configuration of a PersistenceManagerFactory provider bean</b>
 *
 * <p>As alternative to the properties-driven approach that this FactoryBean offers
 * (which is analogous to using the standard JDOHelper class with a Properties
 * object that is populated with standard JDO properties), you can set up an
 * instance of your PersistenceManagerFactory implementation class directly.
 *
 * <p>Like a DataSource, a PersistenceManagerFactory is encouraged to
 * support bean-style configuration, which makes it very easy to set up as
 * Spring-managed bean. The implementation class becomes the bean class;
 * the remaining properties are applied as bean properties (starting with
 * lower-case characters, in contrast to the corresponding JDO properties).
 *
 * <p>For example, in case of <a href="http://www.jpox.org">JPOX</a>:
 *
 * <p><pre>
 * &lt;bean id="persistenceManagerFactory" class="org.jpox.PersistenceManagerFactoryImpl" destroy-method="close"&gt;
 *   &lt;property name="connectionFactory" ref="dataSource"/&gt;
 *   &lt;property name="nontransactionalRead" value="true"/&gt;
 * &lt;/bean&gt;
 * </pre>
 *
 * <p>Note that such direct setup of a PersistenceManagerFactory implementation
 * is the only way to pass an external connection factory (i.e. a JDBC DataSource)
 * into a JDO PersistenceManagerFactory. With the standard properties-driven approach,
 * you can only use an internal connection pool or a JNDI DataSource.
 *
 * <p>The <code>close()</code> method is standardized in JDO; don't forget to
 * specify it as "destroy-method" for any PersistenceManagerFactory instance.
 * Note that this FactoryBean will automatically invoke <code>close()</code> for
 * the PersistenceManagerFactory that it creates, without any special configuration.
 *
 * @author Juergen Hoeller
 * @since 03.06.2003
 * @see JdoTemplate#setPersistenceManagerFactory
 * @see JdoTransactionManager#setPersistenceManagerFactory
 * @see org.springframework.jndi.JndiObjectFactoryBean
 * @see javax.jdo.JDOHelper#getPersistenceManagerFactory
 * @see javax.jdo.PersistenceManagerFactory#setConnectionFactory
 * @see javax.jdo.PersistenceManagerFactory#close()
 * @see org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor
 */
public class LocalPersistenceManagerFactoryBean
		implements FactoryBean, BeanClassLoaderAware, InitializingBean, DisposableBean, PersistenceExceptionTranslator {

	protected final Log logger = LogFactory.getLog(getClass());

	private String persistenceManagerFactoryName;

	private Resource configLocation;

	private final Map<String, Object> jdoPropertyMap = new HashMap<String, Object>();

	private ClassLoader beanClassLoader;

	private PersistenceManagerFactory persistenceManagerFactory;

	private JdoDialect jdoDialect;


	/**
	 * Specify the name of the desired PersistenceManagerFactory.
	 * <p>This may either be a properties resource in the classpath if such a resource exists
	 * (JDO 2.0), or a PMF definition with that name from "META-INF/jdoconfig.xml" (JDO 2.1),
	 * or a JPA EntityManagerFactory cast to a PersistenceManagerFactory based on the
	 * persistence-unit name from "META-INF/persistence.xml" (JDO 2.1 / JPA 1.0).
	 * <p>Default is none: Either 'persistenceManagerFactoryName' or 'configLocation'
	 * or 'jdoProperties' needs to be specified.
	 * @see #setConfigLocation
	 * @see #setJdoProperties
	 */
	public void setPersistenceManagerFactoryName(String persistenceManagerFactoryName) {
		this.persistenceManagerFactoryName = persistenceManagerFactoryName;
	}

	/**
	 * Set the location of the JDO properties config file, for example
	 * as classpath resource "classpath:kodo.properties".
	 * <p>Note: Can be omitted when all necessary properties are
	 * specified locally via this bean.
	 */
	public void setConfigLocation(Resource configLocation) {
		this.configLocation = configLocation;
	}

	/**
	 * Set JDO properties, such as"javax.jdo.PersistenceManagerFactoryClass".
	 * <p>Can be used to override values in a JDO properties config file,
	 * or to specify all necessary properties locally.
	 * <p>Can be populated with a String "value" (parsed via PropertiesEditor)
	 * or a "props" element in XML bean definitions.
	 */
	public void setJdoProperties(Properties jdoProperties) {
		CollectionUtils.mergePropertiesIntoMap(jdoProperties, this.jdoPropertyMap);
	}

	/**
	 * Specify JDO properties as a Map, to be passed into
	 * <code>JDOHelper.getPersistenceManagerFactory</code> (if any).
	 * <p>Can be populated with a "map" or "props" element in XML bean definitions.
	 * @see javax.jdo.JDOHelper#getPersistenceManagerFactory(java.util.Map)
	 */
	public void setJdoPropertyMap(Map<String, Object> jdoProperties) {
		if (jdoProperties != null) {
			this.jdoPropertyMap.putAll(jdoProperties);
		}
	}

	/**
	 * Allow Map access to the JDO properties to be passed to the JDOHelper,
	 * with the option to add or override specific entries.
	 * <p>Useful for specifying entries directly, for example via
	 * "jdoPropertyMap[myKey]".
	 */
	public Map<String, Object> getJdoPropertyMap() {
		return this.jdoPropertyMap;
	}
	/**
	 * Set the JDO dialect to use for the PersistenceExceptionTranslator
	 * functionality of this factory.
	 * <p>Default is a DefaultJdoDialect based on the PersistenceManagerFactory's
	 * underlying DataSource, if any.
	 * @see JdoDialect#translateException
	 * @see #translateExceptionIfPossible
	 * @see org.springframework.dao.support.PersistenceExceptionTranslator
	 */
	public void setJdoDialect(JdoDialect jdoDialect) {
		this.jdoDialect = jdoDialect;
	}

	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}


	/**
	 * Initialize the PersistenceManagerFactory for the given location.
	 * @throws IllegalArgumentException in case of illegal property values
	 * @throws IOException if the properties could not be loaded from the given location
	 * @throws JDOException in case of JDO initialization errors
	 */
	public void afterPropertiesSet() throws IllegalArgumentException, IOException, JDOException {
		if (this.persistenceManagerFactoryName != null) {
			if (this.configLocation != null || !this.jdoPropertyMap.isEmpty()) {
				throw new IllegalStateException("'configLocation'/'jdoProperties' not supported in " +
						"combination with 'persistenceManagerFactoryName' - specify one or the other, not both");
			}
			if (logger.isInfoEnabled()) {
				logger.info("Building new JDO PersistenceManagerFactory for name '" +
						this.persistenceManagerFactoryName + "'");
			}
			this.persistenceManagerFactory = newPersistenceManagerFactory(this.persistenceManagerFactoryName);
		}

		else {
			Map<String, Object> mergedProps = new HashMap<String, Object>();
			if (this.configLocation != null) {
				if (logger.isInfoEnabled()) {
					logger.info("Loading JDO config from [" + this.configLocation + "]");
				}
				CollectionUtils.mergePropertiesIntoMap(
						PropertiesLoaderUtils.loadProperties(this.configLocation), mergedProps);
			}
			mergedProps.putAll(this.jdoPropertyMap);
			logger.info("Building new JDO PersistenceManagerFactory");
			this.persistenceManagerFactory = newPersistenceManagerFactory(mergedProps);
		}

		// Build default JdoDialect if none explicitly specified.
		if (this.jdoDialect == null) {
			this.jdoDialect = new DefaultJdoDialect(this.persistenceManagerFactory.getConnectionFactory());
		}
	}

	/**
	 * Subclasses can override this to perform custom initialization of the
	 * PersistenceManagerFactory instance, creating it for the specified name.
	 * <p>The default implementation invokes JDOHelper's
	 * <code>getPersistenceManagerFactory(String)</code> method.
	 * A custom implementation could prepare the instance in a specific way,
	 * or use a custom PersistenceManagerFactory implementation.
	 * @param name the name of the desired PersistenceManagerFactory
	 * @return the PersistenceManagerFactory instance
	 * @see javax.jdo.JDOHelper#getPersistenceManagerFactory(String)
	 */
	protected PersistenceManagerFactory newPersistenceManagerFactory(String name) {
		return JDOHelper.getPersistenceManagerFactory(name, this.beanClassLoader);
	}

	/**
	 * Subclasses can override this to perform custom initialization of the
	 * PersistenceManagerFactory instance, creating it via the given Properties
	 * that got prepared by this LocalPersistenceManagerFactoryBean.
	 * <p>The default implementation invokes JDOHelper's
	 * <code>getPersistenceManagerFactory(Map)</code> method.
	 * A custom implementation could prepare the instance in a specific way,
	 * or use a custom PersistenceManagerFactory implementation.
	 * @param props the merged properties prepared by this LocalPersistenceManagerFactoryBean
	 * @return the PersistenceManagerFactory instance
	 * @see javax.jdo.JDOHelper#getPersistenceManagerFactory(java.util.Map)
	 */
	protected PersistenceManagerFactory newPersistenceManagerFactory(Map props) {
		return JDOHelper.getPersistenceManagerFactory(props, this.beanClassLoader);
	}


	/**
	 * Return the singleton PersistenceManagerFactory.
	 */
	public Object getObject() {
		return this.persistenceManagerFactory;
	}

	public Class getObjectType() {
		return (this.persistenceManagerFactory != null ?
		    this.persistenceManagerFactory.getClass() : PersistenceManagerFactory.class);
	}

	public boolean isSingleton() {
		return true;
	}


	/**
	 * Implementation of the PersistenceExceptionTranslator interface,
	 * as autodetected by Spring's PersistenceExceptionTranslationPostProcessor.
	 * <p>Converts the exception if it is a JDOException, preferably using a specified
	 * JdoDialect. Else returns <code>null</code> to indicate an unknown exception.
	 * @see org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor
	 * @see JdoDialect#translateException
	 * @see PersistenceManagerFactoryUtils#convertJdoAccessException
	 */
	public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
		if (ex instanceof JDOException) {
			if (this.jdoDialect != null) {
				return this.jdoDialect.translateException((JDOException) ex);
			}
			else {
				return PersistenceManagerFactoryUtils.convertJdoAccessException((JDOException) ex);
			}
		}
		return null;
	}


	/**
	 * Close the PersistenceManagerFactory on bean factory shutdown.
	 */
	public void destroy() {
		logger.info("Closing JDO PersistenceManagerFactory");
		this.persistenceManagerFactory.close();
	}

}

/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.ejb.access;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.rmi.RemoteException;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import javax.ejb.EJBObject;
import javax.naming.NamingException;

import org.junit.Test;
import org.springframework.jndi.JndiTemplate;
import org.springframework.remoting.RemoteAccessException;

/**
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 21.05.2003
 */
public class SimpleRemoteStatelessSessionProxyFactoryBeanTests extends SimpleRemoteSlsbInvokerInterceptorTests {

	protected SimpleRemoteSlsbInvokerInterceptor createInterceptor() {
		return new SimpleRemoteStatelessSessionProxyFactoryBean();
	}

	protected Object configuredProxy(SimpleRemoteSlsbInvokerInterceptor si, Class<?> ifc) throws NamingException {
		SimpleRemoteStatelessSessionProxyFactoryBean fb = (SimpleRemoteStatelessSessionProxyFactoryBean) si;
		fb.setBusinessInterface(ifc);
		fb.afterPropertiesSet();
		return fb.getObject();
	}

	@Test
	public void testInvokesMethod() throws Exception {
		final int value = 11;
		final String jndiName = "foo";

		MyEjb myEjb = createMock(MyEjb.class);
		expect(myEjb.getValue()).andReturn(value);
		myEjb.remove();
		replay(myEjb);

		final MyHome home = createMock(MyHome.class);
		expect(home.create()).andReturn(myEjb);
		replay(home);

		JndiTemplate jt = new JndiTemplate() {
			public Object lookup(String name) {
				// parameterize
				assertTrue(name.equals("java:comp/env/" + jndiName));
				return home;
			}
		};

		SimpleRemoteStatelessSessionProxyFactoryBean fb = new SimpleRemoteStatelessSessionProxyFactoryBean();
		fb.setJndiName(jndiName);
		fb.setResourceRef(true);
		fb.setBusinessInterface(MyBusinessMethods.class);
		fb.setJndiTemplate(jt);

		// Need lifecycle methods
		fb.afterPropertiesSet();

		MyBusinessMethods mbm = (MyBusinessMethods) fb.getObject();
		assertTrue(Proxy.isProxyClass(mbm.getClass()));
		assertEquals("Returns expected value", value, mbm.getValue());
		verify(myEjb);
		verify(home);
	}

	@Test
	public void testInvokesMethodOnEjb3StyleBean() throws Exception {
		final int value = 11;
		final String jndiName = "foo";

		final MyEjb myEjb = createMock(MyEjb.class);
		expect(myEjb.getValue()).andReturn(value);
		replay(myEjb);

		JndiTemplate jt = new JndiTemplate() {
			public Object lookup(String name) {
				// parameterize
				assertTrue(name.equals("java:comp/env/" + jndiName));
				return myEjb;
			}
		};

		SimpleRemoteStatelessSessionProxyFactoryBean fb = new SimpleRemoteStatelessSessionProxyFactoryBean();
		fb.setJndiName(jndiName);
		fb.setResourceRef(true);
		fb.setBusinessInterface(MyBusinessMethods.class);
		fb.setJndiTemplate(jt);

		// Need lifecycle methods
		fb.afterPropertiesSet();

		MyBusinessMethods mbm = (MyBusinessMethods) fb.getObject();
		assertTrue(Proxy.isProxyClass(mbm.getClass()));
		assertEquals("Returns expected value", value, mbm.getValue());
		verify(myEjb);
	}

	@Test
	public void testRemoteException() throws Exception {
		final RemoteException rex = new RemoteException();
		final String jndiName = "foo";

		MyEjb myEjb = createMock(MyEjb.class);
		expect(myEjb.getValue()).andThrow(rex);
		// TODO might want to control this behaviour...
		// Do we really want to call remove after a remote exception?
		myEjb.remove();
		replay(myEjb);

		final MyHome home = createMock(MyHome.class);
		expect(home.create()).andReturn(myEjb);
		replay(home);

		JndiTemplate jt = new JndiTemplate() {
			public Object lookup(String name) {
				// parameterize
				assertTrue(name.equals("java:comp/env/" + jndiName));
				return home;
			}
		};

		SimpleRemoteStatelessSessionProxyFactoryBean fb = new SimpleRemoteStatelessSessionProxyFactoryBean();
		fb.setJndiName(jndiName);
		fb.setResourceRef(true);
		fb.setBusinessInterface(MyBusinessMethods.class);
		fb.setJndiTemplate(jt);

		// Need lifecycle methods
		fb.afterPropertiesSet();

		MyBusinessMethods mbm = (MyBusinessMethods) fb.getObject();
		assertTrue(Proxy.isProxyClass(mbm.getClass()));
		try {
			mbm.getValue();
			fail("Should've thrown remote exception");
		}
		catch (RemoteException ex) {
			assertSame("Threw expected RemoteException", rex, ex);
		}
		verify(myEjb);
		verify(home);
	}

	@Test
	public void testCreateException() throws Exception {
		final String jndiName = "foo";

		final CreateException cex = new CreateException();
		final MyHome home = createMock(MyHome.class);
		expect(home.create()).andThrow(cex);
		replay(home);

		JndiTemplate jt = new JndiTemplate() {
			public Object lookup(String name) {
				// parameterize
				assertTrue(name.equals(jndiName));
				return home;
			}
		};

		SimpleRemoteStatelessSessionProxyFactoryBean fb = new SimpleRemoteStatelessSessionProxyFactoryBean();
		fb.setJndiName(jndiName);
		// rely on default setting of resourceRef=false, no auto addition of java:/comp/env prefix
		fb.setBusinessInterface(MyBusinessMethods.class);
		assertEquals(fb.getBusinessInterface(), MyBusinessMethods.class);
		fb.setJndiTemplate(jt);

		// Need lifecycle methods
		fb.afterPropertiesSet();

		MyBusinessMethods mbm = (MyBusinessMethods) fb.getObject();
		assertTrue(Proxy.isProxyClass(mbm.getClass()));

		try {
			mbm.getValue();
			fail("Should have failed to create EJB");
		}
		catch (RemoteException ex) {
			// expected
		}

		verify(home);
	}

	@Test
	public void testCreateExceptionWithLocalBusinessInterface() throws Exception {
		final String jndiName = "foo";

		final CreateException cex = new CreateException();
		final MyHome home = createMock(MyHome.class);
		expect(home.create()).andThrow(cex);
		replay(home);

		JndiTemplate jt = new JndiTemplate() {
			public Object lookup(String name) {
				// parameterize
				assertTrue(name.equals(jndiName));
				return home;
			}
		};

		SimpleRemoteStatelessSessionProxyFactoryBean fb = new SimpleRemoteStatelessSessionProxyFactoryBean();
		fb.setJndiName(jndiName);
		// rely on default setting of resourceRef=false, no auto addition of java:/comp/env prefix
		fb.setBusinessInterface(MyLocalBusinessMethods.class);
		assertEquals(fb.getBusinessInterface(), MyLocalBusinessMethods.class);
		fb.setJndiTemplate(jt);

		// Need lifecycle methods
		fb.afterPropertiesSet();

		MyLocalBusinessMethods mbm = (MyLocalBusinessMethods) fb.getObject();
		assertTrue(Proxy.isProxyClass(mbm.getClass()));

		try {
			mbm.getValue();
			fail("Should have failed to create EJB");
		}
		catch (RemoteAccessException ex) {
			assertTrue(ex.getCause() == cex);
		}

		verify(home);
	}

	@Test
	public void testNoBusinessInterfaceSpecified() throws Exception {
		// Will do JNDI lookup to get home but won't call create
		// Could actually try to figure out interface from create?
		final String jndiName = "foo";

		final MyHome home = createMock(MyHome.class);
		replay(home);

		JndiTemplate jt = new JndiTemplate() {
			public Object lookup(String name) throws NamingException {
				// parameterize
				assertTrue(name.equals(jndiName));
				return home;
			}
		};

		SimpleRemoteStatelessSessionProxyFactoryBean fb = new SimpleRemoteStatelessSessionProxyFactoryBean();
		fb.setJndiName(jndiName);
		// rely on default setting of resourceRef=false, no auto addition of java:/comp/env prefix
		// Don't set business interface
		fb.setJndiTemplate(jt);

		// Check it's a singleton
		assertTrue(fb.isSingleton());

		try {
			fb.afterPropertiesSet();
			fail("Should have failed to create EJB");
		}
		catch (IllegalArgumentException ex) {
			// TODO more appropriate exception?
			assertTrue(ex.getMessage().indexOf("businessInterface") != 1);
		}

		// Expect no methods on home
		verify(home);
	}


	protected static interface MyHome extends EJBHome {

		MyBusinessMethods create() throws CreateException, RemoteException;
	}


	protected static interface MyBusinessMethods  {

		int getValue() throws RemoteException;
	}


	protected static interface MyLocalBusinessMethods  {

		int getValue();
	}


	protected static interface MyEjb extends EJBObject, MyBusinessMethods {

	}

}

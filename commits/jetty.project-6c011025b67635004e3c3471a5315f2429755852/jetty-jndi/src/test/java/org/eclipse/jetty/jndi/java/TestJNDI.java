//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.jndi.java;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

import org.eclipse.jetty.jndi.NamingContext;
import org.eclipse.jetty.jndi.local.localContextRoot;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 */
public class TestJNDI
{
    private static final Logger LOG = Log.getLogger(TestJNDI.class);

    static
    {
        // NamingUtil.__log.setDebugEnabled(true);
    }

    public static class MyObjectFactory implements ObjectFactory
    {
        public static String myString = "xxx";

        public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable environment) throws Exception
        {
            return myString;
        }

    }

    @Test
    public void testIt() throws Exception
    {
        try
        {
            //set up some classloaders
            Thread currentThread = Thread.currentThread();
            ClassLoader currentLoader = currentThread.getContextClassLoader();
            ClassLoader childLoader1 = new URLClassLoader(new URL[0], currentLoader);
            ClassLoader childLoader2 = new URLClassLoader(new URL[0], currentLoader);

            /*
            javaRootURLContext.getRoot().addListener(new NamingContext.Listener()
            {
                public void unbind(NamingContext ctx, Binding binding)
                {
                    System.err.println("java unbind "+binding+" from "+ctx.getName());
                }

                public Binding bind(NamingContext ctx, Binding binding)
                {
                    System.err.println("java bind "+binding+" to "+ctx.getName());
                    return binding;
                }
            });

            localContextRoot.getRoot().addListener(new NamingContext.Listener()
            {
                public void unbind(NamingContext ctx, Binding binding)
                {
                    System.err.println("local unbind "+binding+" from "+ctx.getName());
                }

                public Binding bind(NamingContext ctx, Binding binding)
                {
                    System.err.println("local bind "+binding+" to "+ctx.getName());
                    return binding;
                }
            });
            */


            //set the current thread's classloader
            currentThread.setContextClassLoader(childLoader1);

            InitialContext initCtxA = new InitialContext();
            initCtxA.bind ("blah", "123");
            assertEquals ("123", initCtxA.lookup("blah"));

            InitialContext initCtx = new InitialContext();
            Context sub0 = (Context)initCtx.lookup("java:");

            if(LOG.isDebugEnabled())LOG.debug("------ Looked up java: --------------");

            Name n = sub0.getNameParser("").parse("/red/green/");

            if(LOG.isDebugEnabled())LOG.debug("get(0)="+n.get(0));
            if(LOG.isDebugEnabled())LOG.debug("getPrefix(1)="+n.getPrefix(1));
            n = n.getSuffix(1);
            if(LOG.isDebugEnabled())LOG.debug("getSuffix(1)="+n);
            if(LOG.isDebugEnabled())LOG.debug("get(0)="+n.get(0));
            if(LOG.isDebugEnabled())LOG.debug("getPrefix(1)="+n.getPrefix(1));
            n = n.getSuffix(1);
            if(LOG.isDebugEnabled())LOG.debug("getSuffix(1)="+n);
            if(LOG.isDebugEnabled())LOG.debug("get(0)="+n.get(0));
            if(LOG.isDebugEnabled())LOG.debug("getPrefix(1)="+n.getPrefix(1));
            n = n.getSuffix(1);
            if(LOG.isDebugEnabled())LOG.debug("getSuffix(1)="+n);

            n = sub0.getNameParser("").parse("pink/purple/");
            if(LOG.isDebugEnabled())LOG.debug("get(0)="+n.get(0));
            if(LOG.isDebugEnabled())LOG.debug("getPrefix(1)="+n.getPrefix(1));
            n = n.getSuffix(1);
            if(LOG.isDebugEnabled())LOG.debug("getSuffix(1)="+n);
            if(LOG.isDebugEnabled())LOG.debug("get(0)="+n.get(0));
            if(LOG.isDebugEnabled())LOG.debug("getPrefix(1)="+n.getPrefix(1));

            NamingContext ncontext = (NamingContext)sub0;

            Name nn = ncontext.toCanonicalName(ncontext.getNameParser("").parse("/yellow/blue/"));
            LOG.debug(nn.toString());
            assertEquals (2, nn.size());

            nn = ncontext.toCanonicalName(ncontext.getNameParser("").parse("/yellow/blue"));
            LOG.debug(nn.toString());
            assertEquals (2, nn.size());

            nn = ncontext.toCanonicalName(ncontext.getNameParser("").parse("/"));
            if(LOG.isDebugEnabled())LOG.debug("/ parses as: "+nn+" with size="+nn.size());
            LOG.debug(nn.toString());
            assertEquals (1, nn.size());

            nn = ncontext.toCanonicalName(ncontext.getNameParser("").parse(""));
            LOG.debug(nn.toString());
            assertEquals (0, nn.size());

            Context fee = ncontext.createSubcontext("fee");
            fee.bind ("fi", "88");
            assertEquals("88", initCtxA.lookup("java:/fee/fi"));
            assertEquals("88", initCtxA.lookup("java:/fee/fi/"));
            assertTrue (initCtxA.lookup("java:/fee/") instanceof javax.naming.Context);

            try
            {
                Context sub1 = sub0.createSubcontext ("comp");
                fail("Comp should already be bound");
            }
            catch (NameAlreadyBoundException e)
            {
                //expected exception
            }

            //check bindings at comp
            Context sub1 = (Context)initCtx.lookup("java:comp");

            Context sub2 = sub1.createSubcontext ("env");

            initCtx.bind ("java:comp/env/rubbish", "abc");
            assertEquals ("abc", initCtx.lookup("java:comp/env/rubbish"));

            //check binding LinkRefs
            LinkRef link = new LinkRef ("java:comp/env/rubbish");
            initCtx.bind ("java:comp/env/poubelle", link);
            assertEquals ("abc", initCtx.lookup("java:comp/env/poubelle"));

            //check binding References
            StringRefAddr addr = new StringRefAddr("blah", "myReferenceable");
            Reference ref = new Reference (java.lang.String.class.getName(),
                    addr,
                    MyObjectFactory.class.getName(),
                    null);

            initCtx.bind ("java:comp/env/quatsch", ref);
            assertEquals (MyObjectFactory.myString, initCtx.lookup("java:comp/env/quatsch"));

            //test binding something at java:
            Context sub3 = initCtx.createSubcontext("java:zero");
            initCtx.bind ("java:zero/one", "ONE");
            assertEquals ("ONE", initCtx.lookup("java:zero/one"));

            //change the current thread's classloader to check distinct naming
            currentThread.setContextClassLoader(childLoader2);

            Context otherSub1 = (Context)initCtx.lookup("java:comp");
            assertTrue (!(sub1 == otherSub1));
            try
            {
                initCtx.lookup("java:comp/env/rubbish");
            }
            catch (NameNotFoundException e)
            {
                //expected
            }

            //put the thread's classloader back
            currentThread.setContextClassLoader(childLoader1);

            //test rebind with existing binding
            initCtx.rebind("java:comp/env/rubbish", "xyz");
            assertEquals ("xyz", initCtx.lookup("java:comp/env/rubbish"));

            //test rebind with no existing binding
            initCtx.rebind ("java:comp/env/mullheim", "hij");
            assertEquals ("hij", initCtx.lookup("java:comp/env/mullheim"));

            //test that the other bindings are already there
            assertEquals ("xyz", initCtx.lookup("java:comp/env/poubelle"));

            //test java:/comp/env/stuff
            assertEquals ("xyz", initCtx.lookup("java:/comp/env/poubelle/"));

            //test list Names
            NamingEnumeration nenum = initCtx.list ("java:comp/env");
            HashMap results = new HashMap();
            while (nenum.hasMore())
            {
                NameClassPair ncp = (NameClassPair)nenum.next();
                results.put (ncp.getName(), ncp.getClassName());
            }

            assertEquals (4, results.size());

            assertEquals ("java.lang.String", results.get("rubbish"));
            assertEquals ("javax.naming.LinkRef", results.get("poubelle"));
            assertEquals ("java.lang.String", results.get("mullheim"));
            assertEquals ("javax.naming.Reference", results.get("quatsch"));

            //test list Bindings
            NamingEnumeration benum = initCtx.list("java:comp/env");
            assertEquals (4, results.size());

            //test NameInNamespace
            assertEquals ("comp/env", sub2.getNameInNamespace());

            //test close does nothing
            Context closeCtx = (Context)initCtx.lookup("java:comp/env");
            closeCtx.close();


            //test what happens when you close an initial context
            InitialContext closeInit = new InitialContext();
            closeInit.close();

            //check locking the context
            Context ectx = (Context)initCtx.lookup("java:comp");
            ectx.bind("crud", "xxx");
            ectx.addToEnvironment("org.eclipse.jndi.immutable", "TRUE");
            assertEquals ("xxx", initCtx.lookup("java:comp/crud"));
            try
            {
                ectx.bind("crud2", "xxx2");
            }
            catch (NamingException ne)
            {
                //expected failure to modify immutable context
            }

            System.err.println("java:"+javaRootURLContext.getRoot().dump());
            System.err.println("local:"+localContextRoot.getRoot().dump());

            //test what happens when you close an initial context that was used
            initCtx.close();
        }
        finally
        {
            InitialContext ic = new InitialContext();
            Context comp = (Context)ic.lookup("java:comp");
            comp.destroySubcontext("env");
        }
    }
}

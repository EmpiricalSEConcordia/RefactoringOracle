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

package org.eclipse.jetty.jndi;


import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.WeakHashMap;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.log.Logger;



/**
 * ContextFactory.java
 *
 * This is an object factory that produces a jndi naming
 * context based on a classloader.
 *
 *  It is used for the java:comp context.
 *
 *  This object factory is bound at java:comp. When a
 *  lookup arrives for java:comp,  this object factory
 *  is invoked and will return a context specific to
 *  the caller's environment (so producing the java:comp/env
 *  specific to a webapp).
 *
 *  The context selected is based on classloaders. First
 *  we try looking in at the classloader that is associated
 *  with the current webapp context (if there is one). If
 *  not, we use the thread context classloader.
 *
 * Created: Fri Jun 27 09:26:40 2003
 *
 *
 *
 */
public class ContextFactory implements ObjectFactory
{
    private static Logger __log = NamingUtil.__log;

    /**
     * Map of classloaders to contexts.
     */
    private static final WeakHashMap __contextMap = new WeakHashMap();

    /**
     * Threadlocal for injecting a context to use
     * instead of looking up the map.
     */
    private static final ThreadLocal __threadContext = new ThreadLocal();


    /**
     * Find or create a context which pertains to a classloader.
     *
     * We use either the classloader for the current ContextHandler if
     * we are handling a request, OR we use the thread context classloader
     * if we are not processing a request.
     * @see javax.naming.spi.ObjectFactory#getObjectInstance(java.lang.Object, javax.naming.Name, javax.naming.Context, java.util.Hashtable)
     */
    public Object getObjectInstance (Object obj,
                                     Name name,
                                     Context nameCtx,
                                     Hashtable env)
        throws Exception
    {
        //First, see if we have had a context injected into us to use.
        Context ctx = (Context)__threadContext.get();
        if (ctx != null)
        {
            if(__log.isDebugEnabled()) __log.debug("Using the Context that is bound on the thread");
            return ctx;
        }

        // Next, see if we are in a webapp context, if we are, use
        // the classloader of the webapp to find the right jndi comp context
        ClassLoader loader = null;
        if (ContextHandler.getCurrentContext() != null)
        {
            loader = ContextHandler.getCurrentContext().getContextHandler().getClassLoader();
        }


        if (loader != null)
        {
            if (__log.isDebugEnabled()) __log.debug("Using classloader of current org.eclipse.jetty.server.handler.ContextHandler");
        }
        else
        {
            //Not already in a webapp context, in that case, we try the
            //curren't thread's classloader instead
            loader = Thread.currentThread().getContextClassLoader();
            if (__log.isDebugEnabled()) __log.debug("Using thread context classloader");
        }

        //Get the context matching the classloader
        ctx = (Context)__contextMap.get(loader);

        //The map does not contain an entry for this classloader
        if (ctx == null)
        {
            //Check if a parent classloader has created the context
            ctx = getParentClassLoaderContext(loader);

            //Didn't find a context to match any of the ancestors
            //of the classloader, so make a context
            if (ctx == null)
            {
                Reference ref = (Reference)obj;
                StringRefAddr parserAddr = (StringRefAddr)ref.get("parser");
                String parserClassName = (parserAddr==null?null:(String)parserAddr.getContent());
                NameParser parser = (NameParser)(parserClassName==null?null:loader.loadClass(parserClassName).newInstance());

                ctx = new NamingContext (env,
                                         name.get(0),
                                         (NamingContext)nameCtx,
                                         parser);
                if(__log.isDebugEnabled())__log.debug("No entry for classloader: "+loader);
                __contextMap.put (loader, ctx);
            }
        }

        return ctx;
    }

    /**
     * Keep trying ancestors of the given classloader to find one to which
     * the context is bound.
     * @param loader
     * @return the context from the parent class loader
     */
    public Context getParentClassLoaderContext (ClassLoader loader)
    {
        Context ctx = null;
        ClassLoader cl = loader;
        for (cl = cl.getParent(); (cl != null) && (ctx == null); cl = cl.getParent())
        {
            ctx = (Context)__contextMap.get(cl);
        }

        return ctx;
    }


    /**
     * Associate the given Context with the current thread.
     * resetComponentContext method should be called to reset the context.
     * @param ctx the context to associate to the current thread.
     * @return the previous context associated on the thread (can be null)
     */
    public static Context setComponentContext(final Context ctx)
    {
        Context previous = (Context)__threadContext.get();
        __threadContext.set(ctx);
        return previous;
    }

    /**
     * Set back the context with the given value.
     * Don't return the previous context, use setComponentContext() method for this.
     * @param ctx the context to associate to the current thread.
     */
    public static void resetComponentContext(final Context ctx)
    {
        __threadContext.set(ctx);
    }

    public static void dump(Appendable out, String indent) throws IOException
    {
        out.append("o.e.j.jndi.ContextFactory@").append(Long.toHexString(__contextMap.hashCode())).append("\n");
        int size=__contextMap.size();
        int i=0;
        for (Map.Entry<ClassLoader,NamingContext> entry : ((Map<ClassLoader,NamingContext>)__contextMap).entrySet())
        {
            boolean last=++i==size;
            ClassLoader loader=entry.getKey();
            out.append(indent).append(" +- ").append(loader.getClass().getSimpleName()).append("@").append(Long.toHexString(loader.hashCode())).append(": ");

            NamingContext context = entry.getValue();
            context.dump(out,indent+(last?"    ":" |  "));
        }
    }

}

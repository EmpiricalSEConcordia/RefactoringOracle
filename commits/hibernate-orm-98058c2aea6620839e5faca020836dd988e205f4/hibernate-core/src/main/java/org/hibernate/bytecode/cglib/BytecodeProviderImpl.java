/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 *
 */
package org.hibernate.bytecode.cglib;
import java.lang.reflect.Modifier;
import net.sf.cglib.beans.BulkBean;
import net.sf.cglib.beans.BulkBeanException;
import net.sf.cglib.reflect.FastClass;
import org.hibernate.HibernateLogger;
import org.hibernate.bytecode.BytecodeProvider;
import org.hibernate.bytecode.ProxyFactoryFactory;
import org.hibernate.bytecode.ReflectionOptimizer;
import org.hibernate.bytecode.util.FieldFilter;
import org.hibernate.util.StringHelper;
import org.jboss.logging.Logger;

/**
 * Bytecode provider implementation for CGLIB.
 *
 * @author Steve Ebersole
 *
 * @deprecated Per HHH-5451 support for cglib as a bytecode provider has been deprecated.
 */
@Deprecated
public class BytecodeProviderImpl implements BytecodeProvider {

    private static final HibernateLogger LOG = Logger.getMessageLogger(HibernateLogger.class, BytecodeProviderImpl.class.getName());

    public BytecodeProviderImpl() {
        LOG.deprecated();
    }

    public ProxyFactoryFactory getProxyFactoryFactory() {
        return new ProxyFactoryFactoryImpl();
    }

    public ReflectionOptimizer getReflectionOptimizer(
                                                      Class clazz,
                                                      String[] getterNames,
                                                      String[] setterNames,
                                                      Class[] types) {
        FastClass fastClass;
        BulkBean bulkBean;
        try {
            fastClass = FastClass.create( clazz );
            bulkBean = BulkBean.create( clazz, getterNames, setterNames, types );
            if ( !clazz.isInterface() && !Modifier.isAbstract( clazz.getModifiers() ) ) {
                if ( fastClass == null ) {
                    bulkBean = null;
                }
                else {
                    //test out the optimizer:
                    Object instance = fastClass.newInstance();
                    bulkBean.setPropertyValues( instance, bulkBean.getPropertyValues( instance ) );
                }
            }
        }
        catch( Throwable t ) {
            fastClass = null;
            bulkBean = null;
            if (LOG.isDebugEnabled()) {
                int index = 0;
                if (t instanceof BulkBeanException) index = ((BulkBeanException)t).getIndex();
                if (index >= 0) LOG.debugf("Reflection optimizer disabled for: %s [%s: %s (property %s)",
                                           clazz.getName(),
                                           StringHelper.unqualify(t.getClass().getName()),
                                           t.getMessage(),
                                           setterNames[index]);
                else LOG.debugf("Reflection optimizer disabled for: %s [%s: %s",
                                clazz.getName(),
                                StringHelper.unqualify(t.getClass().getName()),
                                t.getMessage());
            }
        }

        if (fastClass != null && bulkBean != null) return new ReflectionOptimizerImpl(new InstantiationOptimizerAdapter(fastClass),
                                                                                      new AccessOptimizerAdapter(bulkBean, clazz));
        return null;
    }

    public org.hibernate.bytecode.ClassTransformer getTransformer(org.hibernate.bytecode.util.ClassFilter classFilter, FieldFilter fieldFilter) {
        return new CglibClassTransformer( classFilter, fieldFilter );
    }
}

package org.eclipse.jetty.util.annotation;
//========================================================================
//Copyright 2011-2012 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//All rights reserved. This program and the accompanying materials
//are made available under the terms of the Eclipse Public License v1.0
//and Apache License v2.0 which accompanies this distribution.
//The Eclipse Public License is available at
//http://www.eclipse.org/legal/epl-v10.html
//The Apache License v2.0 is available at
//http://www.opensource.org/licenses/apache2.0.php
//You may elect to redistribute this code under either of these licenses.
//========================================================================

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target( { ElementType.METHOD } )
public @interface ManagedOperation
{
    /**
     * Description of the Managed Object
     * 
     * @return
     */
    String value() default "Not Specified";
    
    /**
     * The impact of an operation. 
     * 
     * NOTE: Valid values are UNKNOWN, ACTION, INFO, ACTION_INFO
     * 
     * NOTE: applies to METHOD
     * 
     * @return String representing the impact of the operation
     */
    String impact() default "UNKNOWN";
    
    /**
     * Does the managed field exist on a proxy object?
     * 
     * 
     * @return true if a proxy object is involved
     */
    boolean proxied() default false;
}

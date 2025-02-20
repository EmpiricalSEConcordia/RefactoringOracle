package org.apache.commons.lang.exception;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * The base class of all runtime exceptions which can contain other
 * exceptions.
 *
 * @see org.apache.commons.lang.exception.NestableException
 * @author <a href="mailto:Rafal.Krzewski@e-point.pl">Rafal Krzewski</a>
 * @author <a href="mailto:dlr@collab.net">Daniel Rall</a>
 * @author <a href="mailto:knielsen@apache.org">Kasper Nielsen</a>
 * @author <a href="mailto:steven@caswell.name">Steven Caswell</a>
 * @version $Id: NestableRuntimeException.java,v 1.1 2002/07/19 03:35:54 bayard Exp $
 */
public class NestableRuntimeException extends RuntimeException
    implements Nestable
{
    /**
     * The helper instance which contains much of the code which we
     * delegate to.
     */
    protected NestableDelegate delegate = new NestableDelegate(this);

    /**
     * Holds the reference to the exception or error that caused
     * this exception to be thrown.
     */
    private Throwable cause = null;

    /**
     * Constructs a new <code>NestableRuntimeException</code> without specified
     * detail message.
     */
    public NestableRuntimeException()
    {
        super();
    }

    /**
     * Constructs a new <code>NestableRuntimeException</code> with specified
     * detail message.
     *
     * @param msg The error message.
     */
    public NestableRuntimeException(String msg)
    {
        super(msg);
    }

    /**
     * Constructs a new <code>NestableRuntimeException</code> with specified
     * nested <code>Throwable</code>.
     *
     * @param nested The exception or error that caused this exception
     *               to be thrown.
     */
    public NestableRuntimeException(Throwable cause)
    {
        super();
        this.cause = cause;
    }

    /**
     * Constructs a new <code>NestableRuntimeException</code> with specified
     * detail message and nested <code>Throwable</code>.
     *
     * @param msg    The error message.
     * @param nested The exception or error that caused this exception
     *               to be thrown.
     */
    public NestableRuntimeException(String msg, Throwable cause)
    {
        super(msg);
        this.cause = cause;
    }

    /**
     * @see org.apache.commons.lang.exception.Nestable#getCause()
     */
    public Throwable getCause()
    {
        return cause;
    }

    /**
     * Returns the number of nested <code>Throwable</code>s represented by
     * this <code>Nestable</code>, including this <code>Nestable</code>.
     */
    public int getLength()
    {
        return delegate.getLength();
    }
    
    /**
     * @see org.apache.commons.lang.exception.Nestable#getMessage()
     */
    public String getMessage()
    {
        StringBuffer msg = new StringBuffer();
        String ourMsg = super.getMessage();
        if (ourMsg != null)
        {
            msg.append(ourMsg);
        }
        if (cause != null)
        {
            String causeMsg = cause.getMessage();
            if (causeMsg != null)
            {
                if (ourMsg != null)
                {
                    msg.append(": ");
                }
                msg.append(causeMsg);
            }

        }
        return (msg.length() > 0 ? msg.toString() : null);
    }

    /**
     * Returns the error message of this and any nested <code>Throwable</code>s
     * in an array of Strings, one element for each message. Any
     * <code>Throwable</code> specified without a message is represented in
     * the array by a null.
     */
    public String[] getMessages()
    {
        return delegate.getMessages();
    }
    
    public Throwable getThrowable(int index)
    {
        return delegate.getThrowable(index);
    }
    
    public Throwable[] getThrowables()
    {
        return delegate.getThrowables();
    }
    
    public String getMessage(int index)
    {
        if(index == 0)
        {
            return super.getMessage();
        }
        else
        {
            return delegate.getMessage(index);
        }
    }
    
    /**
     * Returns the index, numbered from 0, of the first occurrence of the
     * specified type in the chain of <code>Throwable</code>s, or -1 if the
     * specified type is not found in the chain. If <code>pos</code> is
     * negative, the effect is the same as if it were 0. If <code>pos</code>
     * is greater than or equal to the length of the chain, the effect is the
     * same as if it were the index of the last element in the chain.
     *
     * @param type <code>Class</code> to be found
     * @return index of the first occurrence of the type in the chain, or -1 if
     * the type is not found
     */
    public int indexOfThrowable(Class type)
    {
        return delegate.indexOfThrowable(0, type);
    }

    /**
     * Returns the index, numbered from 0, of the first <code>Throwable</code>
     * that matches the specified type in the chain of <code>Throwable</code>s
     * with an index greater than or equal to the specified position, or -1 if
     * the type is not found. If <code>pos</code> is negative, the effect is the
     * same as if it were 0. If <code>pos</code> is greater than or equal to the
     * length of the chain, the effect is the same as if it were the index of
     * the last element in the chain.
     *
     * @param type <code>Class</code> to be found
     * @param pos index, numbered from 0, of the starting position in the chain
     * to be searched
     * 
     * @return index of the first occurrence of the type in the chain, or -1 if
     * the type is not found
     */
    public int indexOfThrowable(int pos, Class type)
    {
        return delegate.indexOfThrowable(pos, type);
    }
    
    /**
     * Prints the stack trace of this exception the the standar error
     * stream.
     */
    public void printStackTrace()
    {
        delegate.printStackTrace();
    }

    /**
     * Prints the stack trace of this exception to the specified print stream.
     *
     * @param out <code>PrintStream</code> to use for output.
     */
    public void printStackTrace(PrintStream out)
    {
        delegate.printStackTrace(out);
    }

    /**
     * @see org.apache.commons.lang.exception.Nestable#printStackTrace(PrintWriter out)
     */
    public void printStackTrace(PrintWriter out)
    {
        delegate.printStackTrace(out);
    }

    /**
     * @see org.apache.commons.lang.exception.Nestable#printPartialStackTrace(PrintWriter out)
     */
    public final void printPartialStackTrace(PrintWriter out)
    {
        super.printStackTrace(out);
    }
    
}

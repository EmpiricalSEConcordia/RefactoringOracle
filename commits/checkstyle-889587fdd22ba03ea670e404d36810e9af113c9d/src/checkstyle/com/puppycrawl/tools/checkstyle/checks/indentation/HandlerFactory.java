////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2003  Oliver Burn
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle.checks.indentation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Factory for handlers. Looks up constructor via reflection.
 *
 * @author jrichard
 */
public class HandlerFactory
{
    /**
     * Registered handlers.
     */
    private Map mTypeHandlers = new HashMap();

    /**
     * registers a handler
     *
     * @param aType   type from TokenTypes
     * @param aHandlerClass  the handler to register
     */
    private void register(int aType, Class aHandlerClass)
    {
        try {
            Constructor ctor = aHandlerClass.getConstructor(new Class[] {
                IndentationCheck.class,
                DetailAST.class,             // current AST
                ExpressionHandler.class,     // parent
            });
            mTypeHandlers.put(new Integer(aType), ctor);
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException("couldn't find ctor for "
                                       + aHandlerClass);
        }
        catch (SecurityException e) {
            throw new RuntimeException("couldn't find ctor for "
                                       + aHandlerClass,
                                       e);
        }
    }

    /** creates a HandlerFactory */
    public HandlerFactory()
    {
        register(TokenTypes.CASE_GROUP, CaseHandler.class);
        register(TokenTypes.LITERAL_SWITCH, SwitchHandler.class);
        register(TokenTypes.SLIST, SlistHandler.class);
        register(TokenTypes.PACKAGE_DEF, PackageDefHandler.class);
        register(TokenTypes.LITERAL_ELSE, ElseHandler.class);
        register(TokenTypes.LITERAL_IF, IfHandler.class);
        register(TokenTypes.LITERAL_TRY, TryHandler.class);
        register(TokenTypes.LITERAL_CATCH, CatchHandler.class);
        register(TokenTypes.LITERAL_FINALLY, FinallyHandler.class);
        register(TokenTypes.LITERAL_DO, DoWhileHandler.class);
        register(TokenTypes.LITERAL_WHILE, WhileHandler.class);
        register(TokenTypes.LITERAL_FOR, ForHandler.class);
        register(TokenTypes.METHOD_DEF, MethodDefHandler.class);
        register(TokenTypes.CTOR_DEF, MethodDefHandler.class);
        register(TokenTypes.CLASS_DEF, ClassDefHandler.class);
        register(TokenTypes.OBJBLOCK, ObjectBlockHandler.class);
        register(TokenTypes.INTERFACE_DEF, ClassDefHandler.class);
        register(TokenTypes.IMPORT, ImportHandler.class);
        register(TokenTypes.ARRAY_INIT, ArrayInitHandler.class);
        register(TokenTypes.METHOD_CALL, MethodCallHandler.class);
        register(TokenTypes.CTOR_CALL, MethodCallHandler.class);
        register(TokenTypes.LABELED_STAT, LabelHandler.class);
        register(TokenTypes.LABELED_STAT, LabelHandler.class);
        register(TokenTypes.STATIC_INIT, StaticInitHandler.class);
    }

    /**
     * returns true if this type (form TokenTypes) is handled
     *
     * @param aType type from TokenTypes
     * @return true if handler is registered, false otherwise
     */
    public boolean isHandledType(int aType)
    {
        Set typeSet = mTypeHandlers.keySet();
        return typeSet.contains(new Integer(aType));
    }

    /**
     * gets list of registered handler types
     *
     * @return int[] of TokenType types
     */
    public int[] getHandledTypes()
    {
        Set typeSet = mTypeHandlers.keySet();
        int[] types = new int[typeSet.size()];
        int index = 0;
        for (Iterator i = typeSet.iterator(); i.hasNext(); index++) {
            types[index] = ((Integer) i.next()).intValue();
        }

        return types;
    }

    /**
     * Get the handler for an AST.
     *
     * @param aIndentCheck   the indentation check
     * @param aAst           ast to handle
     * @param aParent        the handler parent of this AST
     *
     * @return the ExpressionHandler for aAst
     */
    public ExpressionHandler getHandler(IndentationCheck aIndentCheck,
        DetailAST aAst, ExpressionHandler aParent)
    {
        int type = aAst.getType();

        ExpressionHandler expHandler = null;
        try {
            Constructor handlerCtor = (Constructor) mTypeHandlers.get(
                new Integer(type));
            if (handlerCtor != null) {
                expHandler = (ExpressionHandler) handlerCtor.newInstance(
                    new Object[] {
                        aIndentCheck,
                        aAst,
                        aParent,
                    }
                );
            }
        }
        catch (InstantiationException e) {
            throw new RuntimeException("couldn't instantiate constructor for "
                                       + aAst, e);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException("couldn't access constructor for "
                                       + aAst,
                                       e);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException("couldn't instantiate constructor for "
                                       + aAst, e);
        }
        if (expHandler == null) {
            throw new RuntimeException("no handler for type " + type);
        }
        return expHandler;
    }
}

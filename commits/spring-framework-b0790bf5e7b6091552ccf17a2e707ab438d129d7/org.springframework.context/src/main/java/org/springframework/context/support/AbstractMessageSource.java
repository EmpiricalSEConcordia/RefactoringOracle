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

package org.springframework.context.support;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.util.ObjectUtils;

/**
 * Abstract implementation of the {@link HierarchicalMessageSource} interface,
 * implementing common handling of message variants, making it easy
 * to implement a specific strategy for a concrete MessageSource.
 *
 * <p>Subclasses must implement the abstract {@link #resolveCode}
 * method. For efficient resolution of messages without arguments, the
 * {@link #resolveCodeWithoutArguments} method should be overridden
 * as well, resolving messages without a MessageFormat being involved.
 *
 * <p><b>Note:</b> By default, message texts are only parsed through
 * MessageFormat if arguments have been passed in for the message. In case
 * of no arguments, message texts will be returned as-is. As a consequence,
 * you should only use MessageFormat escaping for messages with actual
 * arguments, and keep all other messages unescaped. If you prefer to
 * escape all messages, set the "alwaysUseMessageFormat" flag to "true".
 *
 * <p>Supports not only MessageSourceResolvables as primary messages
 * but also resolution of message arguments that are in turn
 * MessageSourceResolvables themselves.
 *
 * <p>This class does not implement caching of messages per code, thus
 * subclasses can dynamically change messages over time. Subclasses are
 * encouraged to cache their messages in a modification-aware fashion,
 * allowing for hot deployment of updated messages.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @see #resolveCode(String, java.util.Locale)
 * @see #resolveCodeWithoutArguments(String, java.util.Locale)
 * @see #setAlwaysUseMessageFormat
 * @see java.text.MessageFormat
 */
public abstract class AbstractMessageSource extends MessageSourceSupport implements HierarchicalMessageSource {

	private MessageSource parentMessageSource;

	private boolean useCodeAsDefaultMessage = false;


	public void setParentMessageSource(MessageSource parent) {
		this.parentMessageSource = parent;
	}

	public MessageSource getParentMessageSource() {
		return this.parentMessageSource;
	}

	/**
	 * Set whether to use the message code as default message instead of
	 * throwing a NoSuchMessageException. Useful for development and debugging.
	 * Default is "false".
	 * <p>Note: In case of a MessageSourceResolvable with multiple codes
	 * (like a FieldError) and a MessageSource that has a parent MessageSource,
	 * do <i>not</i> activate "useCodeAsDefaultMessage" in the <i>parent</i>:
	 * Else, you'll get the first code returned as message by the parent,
	 * without attempts to check further codes.
	 * <p>To be able to work with "useCodeAsDefaultMessage" turned on in the parent,
	 * AbstractMessageSource and AbstractApplicationContext contain special checks
	 * to delegate to the internal <code>getMessageInternal</code> method if available.
	 * In general, it is recommended to just use "useCodeAsDefaultMessage" during
	 * development and not rely on it in production in the first place, though.
	 * @see #getMessage(String, Object[], Locale)
	 * @see #getMessageInternal
	 * @see org.springframework.validation.FieldError
	 */
	public void setUseCodeAsDefaultMessage(boolean useCodeAsDefaultMessage) {
		this.useCodeAsDefaultMessage = useCodeAsDefaultMessage;
	}

	/**
	 * Return whether to use the message code as default message instead of
	 * throwing a NoSuchMessageException. Useful for development and debugging.
	 * Default is "false".
	 * <p>Alternatively, consider overriding the <code>getDefaultMessage</code>
	 * method to return a custom fallback message for an unresolvable code.
	 * @see #getDefaultMessage(String)
	 */
	protected boolean isUseCodeAsDefaultMessage() {
		return this.useCodeAsDefaultMessage;
	}


	public final String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
		String msg = getMessageInternal(code, args, locale);
		if (msg != null) {
			return msg;
		}
		if (defaultMessage == null) {
			String fallback = getDefaultMessage(code);
			if (fallback != null) {
				return fallback;
			}
		}
		return renderDefaultMessage(defaultMessage, args, locale);
	}

	public final String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
		String msg = getMessageInternal(code, args, locale);
		if (msg != null) {
			return msg;
		}
		String fallback = getDefaultMessage(code);
		if (fallback != null) {
			return fallback;
		}
		throw new NoSuchMessageException(code, locale);
	}

	public final String getMessage(MessageSourceResolvable resolvable, Locale locale)
			throws NoSuchMessageException {

		String[] codes = resolvable.getCodes();
		if (codes == null) {
			codes = new String[0];
		}
		for (int i = 0; i < codes.length; i++) {
			String msg = getMessageInternal(codes[i], resolvable.getArguments(), locale);
			if (msg != null) {
				return msg;
			}
		}
		if (resolvable.getDefaultMessage() != null) {
			return renderDefaultMessage(resolvable.getDefaultMessage(), resolvable.getArguments(), locale);
		}
		if (codes.length > 0) {
			String fallback = getDefaultMessage(codes[0]);
			if (fallback != null) {
				return fallback;
			}
		}
		throw new NoSuchMessageException(codes.length > 0 ? codes[codes.length - 1] : null, locale);
	}


	/**
	 * Resolve the given code and arguments as message in the given Locale,
	 * returning <code>null</code> if not found. Does <i>not</i> fall back to
	 * the code as default message. Invoked by <code>getMessage</code> methods.
	 * @param code the code to lookup up, such as 'calculator.noRateSet'
	 * @param args array of arguments that will be filled in for params
	 * within the message
	 * @param locale the Locale in which to do the lookup
	 * @return the resolved message, or <code>null</code> if not found
	 * @see #getMessage(String, Object[], String, Locale)
	 * @see #getMessage(String, Object[], Locale)
	 * @see #getMessage(MessageSourceResolvable, Locale)
	 * @see #setUseCodeAsDefaultMessage
	 */
	protected String getMessageInternal(String code, Object[] args, Locale locale) {
		if (code == null) {
			return null;
		}
		if (locale == null) {
			locale = Locale.getDefault();
		}
		Object[] argsToUse = args;

		if (!isAlwaysUseMessageFormat() && ObjectUtils.isEmpty(args)) {
			// Optimized resolution: no arguments to apply,
			// therefore no MessageFormat needs to be involved.
			// Note that the default implementation still uses MessageFormat;
			// this can be overridden in specific subclasses.
			String message = resolveCodeWithoutArguments(code, locale);
			if (message != null) {
				return message;
			}
		}

		else {
			// Resolve arguments eagerly, for the case where the message
			// is defined in a parent MessageSource but resolvable arguments
			// are defined in the child MessageSource.
			argsToUse = resolveArguments(args, locale);

			MessageFormat messageFormat = resolveCode(code, locale);
			if (messageFormat != null) {
				synchronized (messageFormat) {
					return messageFormat.format(argsToUse);
				}
			}
		}

		// Not found -> check parent, if any.
		return getMessageFromParent(code, argsToUse, locale);
	}

	/**
	 * Try to retrieve the given message from the parent MessageSource, if any.
	 * @param code the code to lookup up, such as 'calculator.noRateSet'
	 * @param args array of arguments that will be filled in for params
	 * within the message
	 * @param locale the Locale in which to do the lookup
	 * @return the resolved message, or <code>null</code> if not found
	 * @see #getParentMessageSource()
	 */
	protected String getMessageFromParent(String code, Object[] args, Locale locale) {
		MessageSource parent = getParentMessageSource();
		if (parent != null) {
			if (parent instanceof AbstractMessageSource) {
				// Call internal method to avoid getting the default code back
				// in case of "useCodeAsDefaultMessage" being activated.
				return ((AbstractMessageSource) parent).getMessageInternal(code, args, locale);
			}
			else {
				// Check parent MessageSource, returning null if not found there.
				return parent.getMessage(code, args, null, locale);
			}
		}
		// Not found in parent either.
		return null;
	}

	/**
	 * Return a fallback default message for the given code, if any.
	 * <p>Default is to return the code itself if "useCodeAsDefaultMessage"
	 * is activated, or return no fallback else. In case of no fallback,
	 * the caller will usually receive a NoSuchMessageException from
	 * <code>getMessage</code>.
	 * @param code the message code that we couldn't resolve
	 * and that we didn't receive an explicit default message for
	 * @return the default message to use, or <code>null</code> if none
	 * @see #setUseCodeAsDefaultMessage
	 */
	protected String getDefaultMessage(String code) {
		if (isUseCodeAsDefaultMessage()) {
			return code;
		}
		return null;
	}

	/**
	 * Render the given default message String. The default message is
	 * passed in as specified by the caller and can be rendered into
	 * a fully formatted default message shown to the user.
	 * <p>The default implementation passes the String to <code>formatMessage</code>,
	 * resolving any argument placeholders found in them. Subclasses may override
	 * this method to plug in custom processing of default messages.
	 * @param defaultMessage the passed-in default message String
	 * @param args array of arguments that will be filled in for params within
	 * the message, or <code>null</code> if none.
	 * @param locale the Locale used for formatting
	 * @return the rendered default message (with resolved arguments)
	 * @see #formatMessage(String, Object[], java.util.Locale)
	 */
	@Override
	protected String renderDefaultMessage(String defaultMessage, Object[] args, Locale locale) {
		return formatMessage(defaultMessage, args, locale);
	}


	/**
	 * Searches through the given array of objects, find any
	 * MessageSourceResolvable objects and resolve them.
	 * <p>Allows for messages to have MessageSourceResolvables as arguments.
	 * @param args array of arguments for a message
	 * @param locale the locale to resolve through
	 * @return an array of arguments with any MessageSourceResolvables resolved
	 */
	@Override
	protected Object[] resolveArguments(Object[] args, Locale locale) {
		if (args == null) {
			return new Object[0];
		}
		List resolvedArgs = new ArrayList(args.length);
		for (int i = 0; i < args.length; i++) {
			if (args[i] instanceof MessageSourceResolvable) {
				resolvedArgs.add(getMessage((MessageSourceResolvable) args[i], locale));
			}
			else {
				resolvedArgs.add(args[i]);
			}
		}
		return resolvedArgs.toArray(new Object[resolvedArgs.size()]);
	}

	/**
	 * Subclasses can override this method to resolve a message without
	 * arguments in an optimized fashion, that is, to resolve a message
	 * without involving a MessageFormat.
	 * <p>The default implementation <i>does</i> use MessageFormat,
	 * through delegating to the <code>resolveCode</code> method.
	 * Subclasses are encouraged to replace this with optimized resolution.
	 * <p>Unfortunately, <code>java.text.MessageFormat</code> is not
	 * implemented in an efficient fashion. In particular, it does not
	 * detect that a message pattern doesn't contain argument placeholders
	 * in the first place. Therefore, it's advisable to circumvent
	 * MessageFormat completely for messages without arguments.
	 * @param code the code of the message to resolve
	 * @param locale the Locale to resolve the code for
	 * (subclasses are encouraged to support internationalization)
	 * @return the message String, or <code>null</code> if not found
	 * @see #resolveCode
	 * @see java.text.MessageFormat
	 */
	protected String resolveCodeWithoutArguments(String code, Locale locale) {
		MessageFormat messageFormat = resolveCode(code, locale);
		if (messageFormat != null) {
			synchronized (messageFormat) {
				return messageFormat.format(new Object[0]);
			}
		}
		return null;
	}

	/**
	 * Subclasses must implement this method to resolve a message.
	 * <p>Returns a MessageFormat instance rather than a message String,
	 * to allow for appropriate caching of MessageFormats in subclasses.
	 * <p><b>Subclasses are encouraged to provide optimized resolution
	 * for messages without arguments, not involving MessageFormat.</b>
	 * See <code>resolveCodeWithoutArguments</code> javadoc for details.
	 * @param code the code of the message to resolve
	 * @param locale the Locale to resolve the code for
	 * (subclasses are encouraged to support internationalization)
	 * @return the MessageFormat for the message, or <code>null</code> if not found
	 * @see #resolveCodeWithoutArguments(String, java.util.Locale)
	 */
	protected abstract MessageFormat resolveCode(String code, Locale locale);

}

/*
 * Copyright 2015-2017 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.api.extension;

import static org.junit.platform.commons.meta.API.Status.STABLE;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.platform.commons.meta.API;
import org.junit.platform.commons.util.PreconditionViolationException;
import org.junit.platform.commons.util.Preconditions;

/**
 * {@code ExtensionContext} encapsulates the <em>context</em> in which the
 * current test or container is being executed.
 *
 * <p>{@link Extension Extensions} are provided an instance of
 * {@code ExtensionContext} to perform their work.
 *
 * @since 5.0
 * @see Store
 * @see Namespace
 */
@API(status = STABLE, since = "5.0")
public interface ExtensionContext {

	/**
	 * Get the parent extension context, if available.
	 *
	 * @return an {@code Optional} containing the parent; never {@code null} but
	 * potentially empty
	 * @see #getRoot()
	 */
	Optional<ExtensionContext> getParent();

	/**
	 * Get the <em>root</em> {@code ExtensionContext}.
	 *
	 * @return the root extension context; never {@code null} but potentially
	 * <em>this</em> {@code ExtensionContext}
	 * @see #getParent()
	 */
	ExtensionContext getRoot();

	/**
	 * Get the unique ID of the current test or container.
	 *
	 * @return the unique ID of the test or container; never {@code null} or blank
	 */
	String getUniqueId();

	/**
	 * Get the display name for the current test or container.
	 *
	 * <p>The display name is either a default name or a custom name configured
	 * via {@link org.junit.jupiter.api.DisplayName @DisplayName}.
	 *
	 * <p>For details on default display names consult the Javadoc for
	 * {@link org.junit.jupiter.api.TestInfo#getDisplayName()}.
	 *
	 * <p>Note that display names are typically used for test reporting in IDEs
	 * and build tools and may contain spaces, special characters, and even emoji.
	 *
	 * @return the display name of the test or container; never {@code null} or blank
	 */
	String getDisplayName();

	/**
	 * Get the set of all tags for the current test or container.
	 *
	 * <p>Tags may be declared directly on the test element or <em>inherited</em>
	 * from an outer context.
	 *
	 * @return the set of tags for the test or container; never {@code null} but
	 * potentially empty
	 */
	Set<String> getTags();

	/**
	 * Get the {@link AnnotatedElement} corresponding to the current extension
	 * context, if available.
	 *
	 * <p>For example, if the current extension context encapsulates a test
	 * class, test method, test factory method, or test template method, the
	 * annotated element will be the corresponding {@link Class} or {@link Method}
	 * reference.
	 *
	 * <p>Favor this method over more specific methods whenever the
	 * {@code AnnotatedElement} API suits the task at hand &mdash; for example,
	 * when looking up annotations regardless of concrete element type.
	 *
	 * @return an {@code Optional} containing the {@code AnnotatedElement};
	 * never {@code null} but potentially empty
	 * @see #getTestClass()
	 * @see #getTestMethod()
	 */
	Optional<AnnotatedElement> getElement();

	/**
	 * Get the {@link Class} associated with the current test or container,
	 * if available.
	 *
	 * @return an {@code Optional} containing the class; never {@code null} but
	 * potentially empty
	 * @see #getRequiredTestClass()
	 */
	Optional<Class<?>> getTestClass();

	/**
	 * Get the <em>required</em> {@link Class} associated with the current test
	 * or container.
	 *
	 * <p>Use this method as an alternative to {@link #getTestClass()} for use
	 * cases in which the test class is required to be present.
	 *
	 * @return the test class; never {@code null}
	 * @throws PreconditionViolationException if the test class is not present
	 * in this {@code ExtensionContext}
	 */
	default Class<?> getRequiredTestClass() {
		return Preconditions.notNull(getTestClass().orElse(null),
			"Illegal state: required test class is not present in the current ExtensionContext");
	}

	/**
	 * Get the test instance associated with the current test or container,
	 * if available.
	 *
	 * @return an {@code Optional} containing the test instance; never
	 * {@code null} but potentially empty
	 * @see #getRequiredTestInstance()
	 */
	Optional<Object> getTestInstance();

	/**
	 * Get the <em>required</em> test instance associated with the current test
	 * or container.
	 *
	 * <p>Use this method as an alternative to {@link #getTestInstance()} for use
	 * cases in which the test instance is required to be present.
	 *
	 * @return the test instance; never {@code null}
	 * @throws PreconditionViolationException if the test instance is not present
	 * in this {@code ExtensionContext}
	 */
	default Object getRequiredTestInstance() {
		return Preconditions.notNull(getTestInstance().orElse(null),
			"Illegal state: required test instance is not present in the current ExtensionContext");
	}

	/**
	 * Get the {@link Method} associated with the current test, if available.
	 *
	 * @return an {@code Optional} containing the method; never {@code null} but
	 * potentially empty
	 * @see #getRequiredTestMethod()
	 */
	Optional<Method> getTestMethod();

	/**
	 * Get the <em>required</em> {@link Method} associated with the current test
	 * or container.
	 *
	 * <p>Use this method as an alternative to {@link #getTestMethod()} for use
	 * cases in which the test method is required to be present.
	 *
	 * @return the test method; never {@code null}
	 * @throws PreconditionViolationException if the test method is not present
	 * in this {@code ExtensionContext}
	 */
	default Method getRequiredTestMethod() {
		return Preconditions.notNull(getTestMethod().orElse(null),
			"Illegal state: required test method is not present in the current ExtensionContext");
	}

	/**
	 * Get the exception that was thrown during execution of the test or container
	 * associated with this {@code ExtensionContext}, if available.
	 *
	 * <p>This method is typically used for logging and tracing purposes. If you
	 * wish to actually <em>handle</em> an exception thrown during test execution,
	 * implement the {@link TestExecutionExceptionHandler} API.
	 *
	 * <p>Unlike the exception passed to a {@code TestExecutionExceptionHandler},
	 * an <em>execution exception</em> returned by this method can be any
	 * exception thrown during the invocation of a {@code @Test} method, its
	 * surrounding {@code @BeforeEach} and {@code @AfterEach} methods, or a
	 * test-level {@link Extension}. Similarly, if this {@code ExtensionContext}
	 * represents a test class, the <em>execution exception</em> returned by
	 * this method can be any exception thrown in a {@code @BeforeAll} or
	 * {@code AfterAll} method or a class-level {@link Extension}.
	 *
	 * <p>Note, however, that this method will never return an exception
	 * swallowed by a {@code TestExecutionExceptionHandler}. Furthermore, if
	 * multiple exceptions have been thrown during test execution, the exception
	 * returned by this method will be the first such exception with all
	 * additional exceptions {@linkplain Throwable#addSuppressed(Throwable)
	 * suppressed} in the first one.
	 *
	 * @return an {@code Optional} containing the exception thrown; never
	 * {@code null} but potentially empty if test execution has not (yet)
	 * resulted in an exception
	 */
	Optional<Throwable> getExecutionException();

	/**
	 * Publish a map of key-value pairs to be consumed by an
	 * {@code org.junit.platform.engine.EngineExecutionListener}.
	 *
	 * @param map the key-value pairs to be published; never {@code null};
	 * keys and values within entries in the map also must not be
	 * {@code null} or blank
	 */
	void publishReportEntry(Map<String, String> map);

	/**
	 * Publish the specified key-value pair to be consumed by an
	 * {@code org.junit.platform.engine.EngineExecutionListener}.
	 *
	 * @param key the key of the published pair; never {@code null} or blank
	 * @param value the value of the published pair; never {@code null} or blank
	 */
	default void publishReportEntry(String key, String value) {
		this.publishReportEntry(Collections.singletonMap(key, value));
	}

	/**
	 * Get the {@link Store} for the supplied {@link Namespace}.
	 *
	 * <p>Use {@code getStore(Namespace.GLOBAL)} to get the default, global {@link Namespace}.
	 *
	 * @param namespace the {@code Namespace} to get the store for; never {@code null}
	 * @return the store in which to put and get objects for other invocations
	 * working in the same namespace; never {@code null}
	 * @see Namespace#GLOBAL
	 */
	Store getStore(Namespace namespace);

	/**
	 * {@code Store} provides methods for extensions to save and retrieve data.
	 */
	interface Store {

		/**
		 * Get the value that is stored under the supplied {@code key}.
		 *
		 * <p>If no value is stored in the current {@link ExtensionContext}
		 * for the supplied {@code key}, ancestors of the context will be queried
		 * for a value with the same {@code key} in the {@code Namespace} used
		 * to create this store.
		 *
		 * <p>For greater type safety, consider using {@link #get(Object, Class)}
		 * instead.
		 *
		 * @param key the key; never {@code null}
		 * @return the value; potentially {@code null}
		 * @see #get(Object, Class)
		 */
		Object get(Object key);

		/**
		 * Get the value of the specified required type that is stored under
		 * the supplied {@code key}.
		 *
		 * <p>If no value is stored in the current {@link ExtensionContext}
		 * for the supplied {@code key}, ancestors of the context will be queried
		 * for a value with the same {@code key} in the {@code Namespace} used
		 * to create this store.
		 *
		 * @param key the key; never {@code null}
		 * @param requiredType the required type of the value; never {@code null}
		 * @param <V> the value type
		 * @return the value; potentially {@code null}
		 * @see #get(Object)
		 */
		<V> V get(Object key, Class<V> requiredType);

		/**
		 * Get the value that is stored under the supplied {@code key}.
		 *
		 * <p>If no value is stored in the current {@link ExtensionContext}
		 * for the supplied {@code key}, ancestors of the context will be queried
		 * for a value with the same {@code key} in the {@code Namespace} used
		 * to create this store. If no value is found for the supplied {@code key},
		 * a new value will be computed by the {@code defaultCreator} (given
		 * the {@code key} as input), stored, and returned.
		 *
		 * <p>For greater type safety, consider using
		 * {@link #getOrComputeIfAbsent(Object, Function, Class)} instead.
		 *
		 * @param key the key; never {@code null}
		 * @param defaultCreator the function called with the supplied {@code key}
		 * to create a new value; never {@code null}
		 * @param <K> the key type
		 * @param <V> the value type
		 * @return the value; potentially {@code null}
		 * @see #getOrComputeIfAbsent(Object, Function, Class)
		 */
		<K, V> Object getOrComputeIfAbsent(K key, Function<K, V> defaultCreator);

		/**
		 * Get the value of the specified required type that is stored under the
		 * supplied {@code key}.
		 *
		 * <p>If no value is stored in the current {@link ExtensionContext}
		 * for the supplied {@code key}, ancestors of the context will be queried
		 * for a value with the same {@code key} in the {@code Namespace} used
		 * to create this store. If no value is found for the supplied {@code key},
		 * a new value will be computed by the {@code defaultCreator} (given
		 * the {@code key} as input), stored, and returned.
		 *
		 * @param key the key; never {@code null}
		 * @param defaultCreator the function called with the supplied {@code key}
		 * to create a new value; never {@code null}
		 * @param requiredType the required type of the value; never {@code null}
		 * @param <K> the key type
		 * @param <V> the value type
		 * @return the value; potentially {@code null}
		 * @see #getOrComputeIfAbsent(Object, Function)
		 */
		<K, V> V getOrComputeIfAbsent(K key, Function<K, V> defaultCreator, Class<V> requiredType);

		/**
		 * Store a {@code value} for later retrieval under the supplied {@code key}.
		 *
		 * <p>A stored {@code value} is visible in child {@link ExtensionContext
		 * ExtensionContexts} for the store's {@code Namespace} unless they
		 * overwrite it.
		 *
		 * @param key the key under which the value should be stored; never
		 * {@code null}
		 * @param value the value to store; may be {@code null}
		 */
		void put(Object key, Object value);

		/**
		 * Remove the value that was previously stored under the supplied {@code key}.
		 *
		 * <p>The value will only be removed in the current {@link ExtensionContext},
		 * not in ancestors.
		 *
		 * <p>For greater type safety, consider using {@link #remove(Object, Class)}
		 * instead.
		 *
		 * @param key the key; never {@code null}
		 * @return the previous value or {@code null} if no value was present
		 * for the specified key
		 * @see #remove(Object, Class)
		 */
		Object remove(Object key);

		/**
		 * Remove the value of the specified required type that was previously stored
		 * under the supplied {@code key}.
		 *
		 * <p>The value will only be removed in the current {@link ExtensionContext},
		 * not in ancestors.
		 *
		 * @param key the key; never {@code null}
		 * @param requiredType the required type of the value; never {@code null}
		 * @param <V> the value type
		 * @return the previous value or {@code null} if no value was present
		 * for the specified key
		 * @see #remove(Object)
		 */
		<V> V remove(Object key, Class<V> requiredType);

	}

	/**
	 * A {@code Namespace} is used to provide a <em>scope</em> for data saved by
	 * extensions within a {@link Store}.
	 *
	 * <p>Storing data in custom namespaces allows extensions to avoid accidentally
	 * mixing data between extensions or across different invocations within the
	 * lifecycle of a single extension.
	 */
	class Namespace {

		/**
		 * The default, global namespace which allows access to stored data from
		 * all extensions.
		 */
		public static final Namespace GLOBAL = Namespace.create(new Object());

		/**
		 * Create a namespace which restricts access to data to all extensions
		 * which use the same sequence of {@code parts} for creating a namespace.
		 *
		 * <p>The order of the {@code parts} is significant.
		 *
		 * <p>Internally the {@code parts} are compared using {@link Object#equals(Object)}.
		 */
		public static Namespace create(Object... parts) {
			Preconditions.notEmpty(parts, "parts array must not be null or empty");
			Preconditions.containsNoNullElements(parts, "individual parts must not be null");
			return new Namespace(parts);
		}

		private final List<?> parts;

		private Namespace(Object... parts) {
			this.parts = new ArrayList<>(Arrays.asList(parts));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Namespace that = (Namespace) o;
			return this.parts.equals(that.parts);
		}

		@Override
		public int hashCode() {
			return parts.hashCode();
		}

	}

}

/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito;

import org.mockito.listeners.InvocationListener;
import org.mockito.mock.MockSettingsInfo;
import org.mockito.stubbing.Answer;

import java.io.Serializable;

/**
 * Allows mock creation with additional mock settings.
 * <p/>
 * Don't use it too often.
 * Consider writing simple tests that use simple mocks.
 * Repeat after me: simple tests push simple, KISSy, readable & maintainable code.
 * If you cannot write a test in a simple way - refactor the code under test.
 * <p/>
 * Examples of mock settings:
 * <pre class="code"><code class="java">
 *   //Creates mock with different default answer & name
 *   Foo mock = mock(Foo.class, withSettings()
 *                                .defaultAnswer(RETURNS_SMART_NULLS)
 *                                .name("cool mockie")
 *                                );
 *
 *   //Creates mock with different default answer, descriptive name and extra interfaces
 *   Foo mock = mock(Foo.class, withSettings()
 *                                .defaultAnswer(RETURNS_SMART_NULLS)
 *                                .name("cool mockie")
 *                                .extraInterfaces(Bar.class));
 * </code></pre>
 * {@link MockSettings} has been introduced for two reasons.
 * Firstly, to make it easy to add another mock setting when the demand comes.
 * Secondly, to enable combining together different mock settings without introducing zillions of overloaded mock() methods.
 */
public interface MockSettings extends Serializable, MockSettingsInfo {

    /**
     * Specifies extra interfaces the mock should implement. Might be useful for legacy code or some corner cases.
     * For background, see issue 51 <a href="http://code.google.com/p/mockito/issues/detail?id=51">here</a>
     * <p>
     * This mysterious feature should be used very occasionally.
     * The object under test should know exactly its collaborators & dependencies.
     * If you happen to use it often than please make sure you are really producing simple, clean & readable code.
     * <p>
     * Examples:
     * <pre class="code"><code class="java">
     *   Foo foo = mock(Foo.class, withSettings().extraInterfaces(Bar.class, Baz.class));
     *
     *   //now, the mock implements extra interfaces, so following casting is possible:
     *   Bar bar = (Bar) foo;
     *   Baz baz = (Baz) foo;
     * </code></pre>
     *
     * @param interfaces extra interfaces the should implement.
     * @return settings instance so that you can fluently specify other settings
     */
    MockSettings extraInterfaces(Class<?>... interfaces);

    /**
     * Specifies mock name. Naming mocks can be helpful for debugging - the name is used in all verification errors.
     * <p>
     * Beware that naming mocks is not a solution for complex code which uses too many mocks or collaborators.
     * <b>If you have too many mocks then refactor the code</b> so that it's easy to test/debug without necessity of naming mocks.
     * <p>
     * <b>If you use &#064;Mock annotation then you've got naming mocks for free!</b> &#064;Mock uses field name as mock name. {@link Mock Read more.}
     * <p>
     * Examples:
     * <pre class="code"><code class="java">
     *   Foo foo = mock(Foo.class, withSettings().name("foo"));
     *
     *   //Below does exactly the same:
     *   Foo foo = mock(Foo.class, "foo");
     * </code></pre>
     * @param name the name of the mock, later used in all verification errors
     * @return settings instance so that you can fluently specify other settings
     */
    MockSettings name(String name);

    /**
     * Specifies the instance to spy on. Makes sense only for spies/partial mocks.
     *
     * Sets the instance that will be spied. Actually copies the internal fields of the passed instance to the mock.
     * <p>
     * As usual you are going to read <b>the partial mock warning</b>:
     * Object oriented programming is more or less about tackling complexity by dividing the complexity into separate, specific, SRPy objects.
     * How does partial mock fit into this paradigm? Well, it just doesn't...
     * Partial mock usually means that the complexity has been moved to a different method on the same object.
     * In most cases, this is not the way you want to design your application.
     * <p>
     * However, there are rare cases when partial mocks come handy:
     * dealing with code you cannot change easily (3rd party interfaces, interim refactoring of legacy code etc.)
     * However, I wouldn't use partial mocks for new, test-driven & well-designed code.
     * <p>
     * Enough warnings about partial mocks, see an example how spiedInstance() works:
     * <pre class="code"><code class="java">
     *   Foo foo = mock(Foo.class, withSettings().spiedInstance(fooInstance));
     *
     *   //Below does exactly the same:
     *   Foo foo = spy(fooInstance);
     * </code></pre>
     *
     * About stubbing for a partial mock, as it is a spy it will always call the real method, unless you use the
     * <code>doReturn</code>|<code>Throw</code>|<code>Answer</code>|<code>CallRealMethod</code> stubbing style. Example:
     *
     * <pre class="code"><code class="java">
     *   List list = new LinkedList();
     *   List spy = spy(list);
     *
     *   //Impossible: real method is called so spy.get(0) throws IndexOutOfBoundsException (the list is yet empty)
     *   when(spy.get(0)).thenReturn("foo");
     *
     *   //You have to use doReturn() for stubbing
     *   doReturn("foo").when(spy).get(0);
     * </code>
     *
     * @param instance to spy on
     * @return settings instance so that you can fluently specify other settings
     */
    MockSettings spiedInstance(Object instance);

    /**
     * Specifies default answers to interactions.
     * It's quite advanced feature and typically you don't need it to write decent tests.
     * However it can be helpful when working with legacy systems.
     * <p>
     * It is the default answer so it will be used <b>only when you don't</b> stub the method call.
     *
     * <pre class="code"><code class="java">
     *   Foo mock = mock(Foo.class, withSettings().defaultAnswer(RETURNS_SMART_NULLS));
     *   Foo mockTwo = mock(Foo.class, withSettings().defaultAnswer(new YourOwnAnswer()));
     *
     *   //Below does exactly the same:
     *   Foo mockTwo = mock(Foo.class, new YourOwnAnswer());
     * </code></pre>
     *
     * @param defaultAnswer default answer to be used by mock when not stubbed
     * @return settings instance so that you can fluently specify other settings
     */
    @SuppressWarnings("unchecked")
    MockSettings defaultAnswer(Answer defaultAnswer);

    /**
     * Configures the mock to be serializable. With this feature you can use a mock in a place that requires dependencies to be serializable.
     * <p>
     * WARNING: This should be rarely used in unit testing.
     * <p>
     * The behaviour was implemented for a specific use case of a BDD spec that had an unreliable external dependency.  This
     * was in a web environment and the objects from the external dependency were being serialized to pass between layers.
     * <p>
     * Example:
     * <pre class="code"><code class="java">
     *   List serializableMock = mock(List.class, withSettings().serializable());
     * </code></pre>
     *
     * @return settings instance so that you can fluently specify other settings
     * @since 1.8.1
     */
    MockSettings serializable();

    /**
     * Enables real-time logging of method invocations on this mock. Can be used
     * during test debugging in order to find wrong interactions with this mock.
     * <p>
     * Invocations are logged as they happen to the standard output stream.
     * <p>
     * Calling this method multiple times makes no difference.
     * <p>
     * Example:
     * <pre class="code"><code class="java">
     * List mockWithLogger = mock(List.class, withSettings().verboseLogging());
     * </code></pre>
     *
     * @return settings instance so that you can fluently specify other settings
     */
    MockSettings verboseLogging();

    /**
     * Registers a listener for method invocations on this mock. The listener is
     * notified every time a method on this mock is called.
     * <p>
     * Multiple listeners may be added, but the same object is only added once.
     * The order, in which the listeners are added, is not guaranteed to be the
     * order in which the listeners are notified.
     *
     * Example:
     * <pre class="code"><code class="java">
     *  List mockWithListener = mock(List.class, withSettings().invocationListeners(new YourInvocationListener()));
     * </code></pre>
     *
     * See the {@link InvocationListener listener interface} for more details.
     *
     * @param listeners The invocation listeners to add. May not be null.
     * @return settings instance so that you can fluently specify other settings
     */
    MockSettings invocationListeners(InvocationListener... listeners);

    /**
     * Specifies the delegated instance on which a mock should forward calls.
     *
     * Makes sense only for spies or partial mocks of objects that are difficult to mock or spy using the usual spy API.
     * Possible use cases :
     * <ul>
     *     <li>Final classes but with an interface</li>
     *     <li>Already custom proxied object</li>
     *     <li>Special objects with a finalize method, i.e. to avoid executing it 2 times</li>
     *     <li>...</li>
     * </ul>
     * Sets the real implementation to be called when the method is called on a mock object.
     * <p>
     * As usual you are going to read <b>the partial mock warning</b>:
     * Object oriented programming is more or less about tackling complexity by dividing the complexity into separate, specific, SRPy objects.
     * How does partial mock fit into this paradigm? Well, it just doesn't...
     * Partial mock usually means that the complexity has been moved to a different method on the same object.
     * In most cases, this is not the way you want to design your application.
     * <p>
     * However, there are rare cases when partial mocks come handy:
     * dealing with code you cannot change easily (3rd party interfaces, interim refactoring of legacy code etc.)
     * However, I wouldn't use partial mocks for new, test-driven & well-designed code.
     * <p>
     * Enough warnings about partial mocks, see an example how spiedInstance() works:
     * <pre class="code"><code class="java">
     *   final class DontMessTheCodeOfThisList implements list { ... }
     *
     *   DontMessTheCodeOfThisList awesomeList = new DontMessTheCodeOfThisList();
     *
     *   List listWithDelegate = mock(List.class, withSettings().forwardTo(awesomeList));
     * </code></pre>
     *
     * <p>
     * This features suffer from the same drawback as the spy. The mock will always call the delegate.
     * This mean that you have to use the doReturn|Throw|Answer|CallRealMethod stubbing style. Example:
     *
     * <pre class="code"><code class="java">
     *   List listWithDelegate = mock(List.class, withSettings().forwardTo(awesomeList));
     *
     *   //Impossible: real method is called so listWithDelegate.get(0) throws IndexOutOfBoundsException (the list is yet empty)
     *   when(listWithDelegate.get(0)).thenReturn("foo");
     *
     *   //You have to use doReturn() for stubbing
     *   doReturn("foo").when(listWithDelegate).get(0);
     * </code></pre>
     *
     * @param delegate The delegate to forward calls to.
     * @return settings instance so that you can fluently specify other settings
     *
     * @since 1.9.5
     */
    MockSettings forwardTo(Object delegate) ;
}

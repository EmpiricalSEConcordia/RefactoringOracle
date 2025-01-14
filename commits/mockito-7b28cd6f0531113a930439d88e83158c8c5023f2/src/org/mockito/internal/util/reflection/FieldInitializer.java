package org.mockito.internal.util.reflection;

import org.mockito.exceptions.base.MockitoException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Initialize a field with type instance if a default constructor can be found.
 *
 * <p>
 * If the given field is already initialized, then <strong>the actual instance is returned</strong>.
 * This initializer doesn't work with inner classes, local classes, interfaces or abstract types.
 * </p>
 *
 */
public class FieldInitializer {

    private Object fieldOwner;
    private Field field;
    private ConstructorInstantiator instantiator;


    /**
     * Prepare initializer with the given field on the given instance.
     *
     * <p>
     * This constructor fail fast if the field type cannot be handled.
     * </p>
     *
     * @param fieldOwner Instance of the test.
     * @param field Field to be initialize.
     */
    public FieldInitializer(Object fieldOwner, Field field) {
        this(fieldOwner, field, new NoArgConstructorInstantiator(fieldOwner, field));
    }

    /**
     * Prepare initializer with the given field on the given instance.
     *
     * <p>
     * This constructor fail fast if the field type cannot be handled.
     * </p>
     *
     * @param fieldOwner Instance of the test.
     * @param field Field to be initialize.
     */
    public FieldInitializer(Object fieldOwner, Field field, ConstructorArgumentResolver argResolver) {
        this(fieldOwner, field, new ParameterizedConstructorInstantiator(fieldOwner, field, argResolver));
    }

    private FieldInitializer(Object fieldOwner, Field field, ConstructorInstantiator instantiator) {
        if(new FieldReader(fieldOwner, field).isNull()) {
            checkNotLocal(field);
            checkNotInner(field);
            checkNotInterface(field);
            checkNotAbstract(field);
        }
        this.fieldOwner = fieldOwner;
        this.field = field;
        this.instantiator = instantiator;
    }

    /**
     * Initialize field if no initialized and return the actual instance.
     *
     * @return Actual field instance.
     */
    public FieldInitializationReport initialize() {
        final AccessibilityChanger changer = new AccessibilityChanger();
        changer.enableAccess(field);

        try {
            return acquireFieldInstance();
        } catch(IllegalAccessException e) {
            throw new MockitoException("Problems initializing field '" + field.getName() + "' of type '" + field.getType().getSimpleName() + "'", e);
        } finally {
            changer.safelyDisableAccess(field);
        }
    }

    private void checkNotLocal(Field field) {
        if(field.getType().isLocalClass()) {
            throw new MockitoException("the type '" + field.getType().getSimpleName() + "' is a local class.");
        }
    }

    private void checkNotInner(Field field) {
        if(field.getType().isMemberClass() && !Modifier.isStatic(field.getType().getModifiers())) {
            throw new MockitoException("the type '" + field.getType().getSimpleName() + "' is an inner class.");
        }
    }

    private void checkNotInterface(Field field) {
        if(field.getType().isInterface()) {
            throw new MockitoException("the type '" + field.getType().getSimpleName() + "' is an interface.");
        }
    }

    private void checkNotAbstract(Field field) {
        if(Modifier.isAbstract(field.getType().getModifiers())) {
            throw new MockitoException("the type '" + field.getType().getSimpleName() + " is an abstract class.");
        }
    }

    private FieldInitializationReport acquireFieldInstance() throws IllegalAccessException {
        Object fieldInstance = field.get(fieldOwner);
        if(fieldInstance != null) {
            return new FieldInitializationReport(fieldInstance, false);
        }

        instantiator.instantiate();
        return new FieldInitializationReport(field.get(fieldOwner), true);
    }

    /**
     * Represents the strategy used to resolve actual instances
     * to be given to a constructor given the argument types.
     */
    public interface ConstructorArgumentResolver {

        /**
         * Try to resolve instances from types.
         *
         * <p>
         * Checks on the real argument type or on the correct argument number
         * will happen during the field initialization {@link FieldInitializer#initialize()}.
         * I.e the only responsibility of this method, is to provide instances <strong>if possible</strong>.
         * </p>
         *
         * @param argTypes Constructor argument types, should not be null.
         * @return The argument instances to be given to the constructor, should not be null.
         */
        Object[] resolveTypeInstances(Class<?>... argTypes);
    }

    private interface ConstructorInstantiator {
        Object instantiate();
    }

    /**
     * Constructor instantiating strategy for no-arg constructor.
     *
     * <p>
     * If a no-arg constructor can be found then the instance is created using
     * this constructor.
     * Otherwise a technical MockitoException is thrown.
     * </p>
     */
    static class NoArgConstructorInstantiator implements ConstructorInstantiator {
        private Object testClass;
        private Field field;

        /**
         * Internal, checks are done by FieldInitializer.
         * Fields are assumed to be accessible.
         */
        NoArgConstructorInstantiator(Object testClass, Field field) {
            this.testClass = testClass;
            this.field = field;
        }

        public Object instantiate() {
            final AccessibilityChanger changer = new AccessibilityChanger();
            Constructor<?> constructor = null;
            try {
                constructor = field.getType().getDeclaredConstructor();
                changer.enableAccess(constructor);

                final Object[] noArg = new Object[0];
                Object newFieldInstance = constructor.newInstance(noArg);
                new FieldSetter(testClass, field).set(newFieldInstance);

                return field.get(testClass);
            } catch (NoSuchMethodException e) {
                throw new MockitoException("the type '" + field.getType().getSimpleName() + "' has no default constructor", e);
            } catch (InvocationTargetException e) {
                throw new MockitoException("the default constructor of type '" + field.getType().getSimpleName() + "' has raised an exception (see the stack trace for cause): " + e.getTargetException().toString(), e);
            } catch (InstantiationException e) {
                throw new MockitoException("InstantiationException (see the stack trace for cause): " + e.toString(), e);
            } catch (IllegalAccessException e) {
                throw new MockitoException("IllegalAccessException (see the stack trace for cause): " + e.toString(), e);
            } finally {
                if(constructor != null) {
                    changer.safelyDisableAccess(constructor);
                }
            }
        }
    }

    /**
     * Constructor instantiating strategy for parameterized constructors.
     *
     * <p>
     * Choose the constructor with the highest number of parameters, then
     * call the ConstructorArgResolver to get actual argument instances.
     * If the argResolver fail, then a technical MockitoException is thrown is thrown.
     * Otherwise the instance is created with the resolved arguments.
     * </p>
     */
    static class ParameterizedConstructorInstantiator implements ConstructorInstantiator {
        private Object testClass;
        private Field field;
        private ConstructorArgumentResolver argResolver;
        private Comparator<Constructor<?>> byParameterNumber = new Comparator<Constructor<?>>() {
            public int compare(Constructor<?> constructorA, Constructor<?> constructorB) {
                return constructorB.getParameterTypes().length - constructorA.getParameterTypes().length;
            }
        };

        /**
         * Internal, checks are done by FieldInitializer.
         * Fields are assumed to be accessible.
         */
        ParameterizedConstructorInstantiator(Object testClass, Field field, ConstructorArgumentResolver argumentResolver) {
            this.testClass = testClass;
            this.field = field;
            this.argResolver = argumentResolver;
        }

        public Object instantiate() {
            final AccessibilityChanger changer = new AccessibilityChanger();
            Constructor<?> constructor = null;
            try {
                constructor = biggestConstructor(field.getType());
                changer.enableAccess(constructor);

                final Object[] args = argResolver.resolveTypeInstances(constructor.getParameterTypes());
                Object newFieldInstance = constructor.newInstance(args);
                new FieldSetter(testClass, field).set(newFieldInstance);

                return field.get(testClass);
            } catch (IllegalArgumentException e) {
                throw new MockitoException("internal error : argResolver provided incorrect types for constructor " + constructor + " of type " + field.getType().getSimpleName(), e);
            } catch (InvocationTargetException e) {
                throw new MockitoException("the constructor of type '" + field.getType().getSimpleName() + "' has raised an exception (see the stack trace for cause): " + e.getTargetException().toString(), e);
            } catch (InstantiationException e) {
                throw new MockitoException("InstantiationException (see the stack trace for cause): " + e.toString(), e);
            } catch (IllegalAccessException e) {
                throw new MockitoException("IllegalAccessException (see the stack trace for cause): " + e.toString(), e);
            } finally {
                if(constructor != null) {
                    changer.safelyDisableAccess(constructor);
                }
            }
        }

        private void checkParameterized(Constructor<?> constructor, Field field) {
            if(constructor.getParameterTypes().length == 0) {
                throw new MockitoException("the field " + field.getName() + " of type " + field.getType() + " has no parameterized constructor");
            }
        }

        private Constructor<?> biggestConstructor(Class<?> clazz) {
            final List<Constructor<?>> constructors = Arrays.asList(clazz.getDeclaredConstructors());
            Collections.sort(constructors, byParameterNumber);

            Constructor<?> constructor = constructors.get(0);
            checkParameterized(constructor, field);
            return constructor;
        }
    }
}

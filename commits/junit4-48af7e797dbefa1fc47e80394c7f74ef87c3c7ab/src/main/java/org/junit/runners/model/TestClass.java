package org.junit.runners.model;

import static java.lang.reflect.Modifier.isStatic;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.internal.MethodSorter;

/**
 * Wraps a class to be run, providing method validation and annotation searching
 *
 * @since 4.5
 */
public class TestClass {
    private final Class<?> fClass;
    private final Map<Class<?>, List<FrameworkMethod>> fMethodsForAnnotations;
    private final Map<Class<?>, List<FrameworkField>> fFieldsForAnnotations;

    /**
     * Creates a {@code TestClass} wrapping {@code klass}. Each time this
     * constructor executes, the class is scanned for annotations, which can be
     * an expensive process (we hope in future JDK's it will not be.) Therefore,
     * try to share instances of {@code TestClass} where possible.
     */
    public TestClass(Class<?> klass) {
        fClass = klass;
        if (klass != null && klass.getConstructors().length > 1) {
            throw new IllegalArgumentException(
                    "Test class can only have one constructor");
        }

        Map<Class<?>, List<FrameworkMethod>> methodsForAnnotations = new HashMap<Class<?>, List<FrameworkMethod>>();
        Map<Class<?>, List<FrameworkField>> fieldsForAnnotations = new HashMap<Class<?>, List<FrameworkField>>();
        for (Class<?> eachClass : getSuperClasses(fClass)) {
            for (Method eachMethod : MethodSorter.getDeclaredMethods(eachClass)) {
                addToAnnotationLists(new FrameworkMethod(eachMethod), methodsForAnnotations);
            }
            for (Field eachField : eachClass.getDeclaredFields()) {
                addToAnnotationLists(new FrameworkField(eachField), fieldsForAnnotations);
            }
        }
        fMethodsForAnnotations = Collections.unmodifiableMap(methodsForAnnotations);
        fFieldsForAnnotations = Collections.unmodifiableMap(fieldsForAnnotations);
    }

    private static <T extends FrameworkMember<T>> void addToAnnotationLists(T member,
            Map<Class<?>, List<T>> map) {
        for (Annotation each : member.getAnnotations()) {
            Class<? extends Annotation> type = each.annotationType();
            List<T> members = getAnnotatedMembers(map, type, true);
            if (member.isShadowedBy(members)) {
                return;
            }
            if (runsTopToBottom(type)) {
                members.add(0, member);
            } else {
                members.add(member);
            }
        }
    }

    /**
     * Returns, efficiently, all the non-overridden methods in this class and
     * its superclasses that are annotated with {@code annotationClass}.
     */
    public List<FrameworkMethod> getAnnotatedMethods(
            Class<? extends Annotation> annotationClass) {
        return Collections.unmodifiableList(getAnnotatedMembers(fMethodsForAnnotations, annotationClass, false));
    }

    /**
     * Returns, efficiently, all the non-overridden methods in this class and
     * its superclasses.
     */
    public Set<FrameworkMethod> getAnnotatedMethods() {
        Set<FrameworkMethod> annotatedMethods = new HashSet<FrameworkMethod>();
        for (List<FrameworkMethod> currentMethods : fMethodsForAnnotations.values()) {
            annotatedMethods.addAll(currentMethods);
        }
        return annotatedMethods;
    }

    /**
     * Returns, efficiently, all the non-overridden fields in this class and its
     * superclasses that are annotated with {@code annotationClass}.
     */
    public List<FrameworkField> getAnnotatedFields(
            Class<? extends Annotation> annotationClass) {
        return Collections.unmodifiableList(getAnnotatedMembers(fFieldsForAnnotations, annotationClass, false));
    }

<<<<<<< HEAD
    private static <T> List<T> getAnnotatedMembers(Map<Class<?>, List<T>> map,
            Class<? extends Annotation> type, boolean fillIfAbsent) {
        if (!map.containsKey(type) && fillIfAbsent) {
=======
    /**
     * Returns, efficiently, all the non-overridden fields in this class and
     * its superclasses.
     */
    public Set<FrameworkField> getAnnotatedFields() {
        Set<FrameworkField> annotatedFields = new HashSet<FrameworkField>();
        for (List<FrameworkField> fields : fFieldsForAnnotations.values()) {
            annotatedFields.addAll(fields);
        }
        return annotatedFields;
    }

    private <T> List<T> getAnnotatedMembers(Map<Class<?>, List<T>> map,
            Class<? extends Annotation> type) {
        if (!map.containsKey(type)) {
>>>>>>> adding annotation validators functionality
            map.put(type, new ArrayList<T>());
        }
        List<T> members = map.get(type);
        return members == null ? Collections.<T>emptyList() : members;
    }

    private static boolean runsTopToBottom(Class<? extends Annotation> annotation) {
        return annotation.equals(Before.class)
                || annotation.equals(BeforeClass.class);
    }

    private static List<Class<?>> getSuperClasses(Class<?> testClass) {
        ArrayList<Class<?>> results = new ArrayList<Class<?>>();
        Class<?> current = testClass;
        while (current != null) {
            results.add(current);
            current = current.getSuperclass();
        }
        return results;
    }

    /**
     * Returns the underlying Java class.
     */
    public Class<?> getJavaClass() {
        return fClass;
    }

    /**
     * Returns the class's name.
     */
    public String getName() {
        if (fClass == null) {
            return "null";
        }
        return fClass.getName();
    }

    /**
     * Returns the only public constructor in the class, or throws an {@code
     * AssertionError} if there are more or less than one.
     */

    public Constructor<?> getOnlyConstructor() {
        Constructor<?>[] constructors = fClass.getConstructors();
        Assert.assertEquals(1, constructors.length);
        return constructors[0];
    }

    /**
     * Returns the annotations on this class
     */
    public Annotation[] getAnnotations() {
        if (fClass == null) {
            return new Annotation[0];
        }
        return fClass.getAnnotations();
    }

    public <T> List<T> getAnnotatedFieldValues(Object test,
            Class<? extends Annotation> annotationClass, Class<T> valueClass) {
        List<T> results = new ArrayList<T>();
        for (FrameworkField each : getAnnotatedFields(annotationClass)) {
            try {
                Object fieldValue = each.get(test);
                if (valueClass.isInstance(fieldValue)) {
                    results.add(valueClass.cast(fieldValue));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "How did getFields return a field we couldn't access?", e);
            }
        }
        return results;
    }

    public <T> List<T> getAnnotatedMethodValues(Object test,
            Class<? extends Annotation> annotationClass, Class<T> valueClass) {
        List<T> results = new ArrayList<T>();
        for (FrameworkMethod each : getAnnotatedMethods(annotationClass)) {
            try {
                Object fieldValue = each.invokeExplosively(test);
                if (valueClass.isInstance(fieldValue)) {
                    results.add(valueClass.cast(fieldValue));
                }
            } catch (Throwable e) {
                throw new RuntimeException(
                        "Exception in " + each.getName(), e);
            }
        }
        return results;
    }

    public boolean isANonStaticInnerClass() {
        return fClass.isMemberClass() && !isStatic(fClass.getModifiers());
    }
}

package org.junit.runners.model;

import static java.lang.reflect.Modifier.isStatic;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Map<Class<? extends Annotation>, List<FrameworkMethod>> fMethodsForAnnotations;
    private final Map<Class<? extends Annotation>, List<FrameworkField>> fFieldsForAnnotations;

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

        Map<Class<? extends Annotation>, List<FrameworkMethod>> methodsForAnnotations =
                new LinkedHashMap<Class<? extends Annotation>, List<FrameworkMethod>>();
        Map<Class<? extends Annotation>, List<FrameworkField>> fieldsForAnnotations =
                new LinkedHashMap<Class<? extends Annotation>, List<FrameworkField>>();

        for (Class<?> eachClass : getSuperClasses(fClass)) {
            for (Method eachMethod : MethodSorter.getDeclaredMethods(eachClass)) {
                addToAnnotationLists(new FrameworkMethod(eachMethod), methodsForAnnotations);
            }
            for (Field eachField : getSortedDeclaredFields(eachClass)) {
                addToAnnotationLists(new FrameworkField(eachField), fieldsForAnnotations);
            }
        }

        Map<Class<? extends Annotation>, List<FrameworkMethod>> methodsForAnnotationsWithUnmodifiableLists =
                new LinkedHashMap<Class<? extends Annotation>, List<FrameworkMethod>>();
        convertListValuesToBeUnmodifiable(methodsForAnnotations, methodsForAnnotationsWithUnmodifiableLists);

        Map<Class<? extends Annotation>, List<FrameworkField>> fieldsForAnnotationsWithUnmodifiableLists =
                new LinkedHashMap<Class<? extends Annotation>, List<FrameworkField>>();
        convertListValuesToBeUnmodifiable(fieldsForAnnotations, fieldsForAnnotationsWithUnmodifiableLists);

        fMethodsForAnnotations = Collections.unmodifiableMap(methodsForAnnotationsWithUnmodifiableLists);
        fFieldsForAnnotations = Collections.unmodifiableMap(fieldsForAnnotationsWithUnmodifiableLists);
    }

    private static Field[] getSortedDeclaredFields(Class<?> clazz) {
        Field[] declaredFields = clazz.getDeclaredFields();
        Arrays.sort(declaredFields, new Comparator<Field>() {
            public int compare(Field field1, Field field2) {
                return field1.getName().compareTo(field2.getName());
            }
        });
        return declaredFields;
    }

    private static <T extends FrameworkMember<T>> void addToAnnotationLists(T member,
            Map<Class<? extends Annotation>, List<T>> map) {
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

    private static <T extends FrameworkMember<T>> void convertListValuesToBeUnmodifiable(
            Map<Class<? extends Annotation>, List<T>> source, Map<Class<? extends Annotation>, List<T>> target) {
        Set<Class<? extends Annotation>> classes = source.keySet();
        for (Class<? extends Annotation> clazz : classes) {
            target.put(clazz, Collections.unmodifiableList(source.get(clazz)));
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
     * Returns, efficiently, all the non-overridden fields in this class and its
     * superclasses that are annotated with {@code annotationClass}.
     */
    public List<FrameworkField> getAnnotatedFields(
            Class<? extends Annotation> annotationClass) {
        return Collections.unmodifiableList(getAnnotatedMembers(fFieldsForAnnotations, annotationClass, false));
    }

    /**
     * Gets a {@code Map} between annotations and methods that have
     * the annotation in this class or its superclasses.
     */
    public Map<Class<? extends Annotation>, List<FrameworkMethod>> getAnnotationToMethods() {
        return fMethodsForAnnotations;
    }

    /**
     * Gets a {@code Map} between annotations and fields that have
     * the annotation in this class or its superclasses.
     */
    public Map<Class<? extends Annotation>, List<FrameworkField>> getAnnotationToFields() {
        return fFieldsForAnnotations;
    }

    private static <T> List<T> getAnnotatedMembers(Map<Class<? extends Annotation>, List<T>> map,
            Class<? extends Annotation> type, boolean fillIfAbsent) {
        if (!map.containsKey(type) && fillIfAbsent) {
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

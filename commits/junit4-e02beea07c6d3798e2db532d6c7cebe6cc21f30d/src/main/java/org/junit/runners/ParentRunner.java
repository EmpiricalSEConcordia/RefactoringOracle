package org.junit.runners;

import static org.junit.internal.runners.rules.RuleFieldValidator.CLASS_RULE_METHOD_VALIDATOR;
import static org.junit.internal.runners.rules.RuleFieldValidator.CLASS_RULE_VALIDATOR;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.validator.AnnotationValidator;
import org.junit.validator.AnnotationValidatorFactory;
import org.junit.validator.ValidateWith;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.rules.RunRules;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerScheduler;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * Provides most of the functionality specific to a Runner that implements a
 * "parent node" in the test tree, with children defined by objects of some data
 * type {@code T}. (For {@link BlockJUnit4ClassRunner}, {@code T} is
 * {@link Method} . For {@link Suite}, {@code T} is {@link Class}.) Subclasses
 * must implement finding the children of the node, describing each child, and
 * running each child. ParentRunner will filter and sort children, handle
 * {@code @BeforeClass} and {@code @AfterClass} methods,
 * handle annotated {@link ClassRule}s, create a composite
 * {@link Description}, and run children sequentially.
 *
 * @since 4.5
 */
public abstract class ParentRunner<T> extends Runner implements Filterable,
        Sortable {
    private final Object fChildrenLock = new Object();
    private final TestClass fTestClass;

    // Guarded by fChildrenLock
    private volatile Collection<T> fFilteredChildren = null;

    private final AnnotationValidatorFactory fAnnotationValidatorFactory =
            new AnnotationValidatorFactory();

    private volatile RunnerScheduler fScheduler = new RunnerScheduler() {
        public void schedule(Runnable childStatement) {
            childStatement.run();
        }

        public void finished() {
            // do nothing
        }
    };

    /**
     * Constructs a new {@code ParentRunner} that will run {@code @TestClass}
     */
    protected ParentRunner(Class<?> testClass) throws InitializationError {
        fTestClass = new TestClass(testClass);
        validate();
    }

    //
    // Must be overridden
    //

    /**
     * Returns a list of objects that define the children of this Runner.
     */
    protected abstract List<T> getChildren();

    /**
     * Returns a {@link Description} for {@code child}, which can be assumed to
     * be an element of the list returned by {@link ParentRunner#getChildren()}
     */
    protected abstract Description describeChild(T child);

    /**
     * Runs the test corresponding to {@code child}, which can be assumed to be
     * an element of the list returned by {@link ParentRunner#getChildren()}.
     * Subclasses are responsible for making sure that relevant test events are
     * reported through {@code notifier}
     */
    protected abstract void runChild(T child, RunNotifier notifier);

    //
    // May be overridden
    //

    /**
     * Adds to {@code errors} a throwable for each problem noted with the test class (available from {@link #getTestClass()}).
     * Default implementation adds an error for each method annotated with
     * {@code @BeforeClass} or {@code @AfterClass} that is not
     * {@code public static void} with no arguments.
     */
    protected void collectInitializationErrors(List<Throwable> errors) {
        validatePublicVoidNoArgMethods(BeforeClass.class, true, errors);
        validatePublicVoidNoArgMethods(AfterClass.class, true, errors);
        validateClassRules(errors);
        invokeValidators(errors);
    }

    private void invokeValidators(List<Throwable> errors) {
        invokeValidatorsOnClass(errors);
        invokeValidatorsOnMethods(errors);
        invokeValidatorsOnFields(errors);
    }

    private void invokeValidatorsOnClass(List<Throwable> errors) {
        Annotation[] annotations = getTestClass().getAnnotations();
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            ValidateWith validateWithAnnotation = annotationType.getAnnotation(ValidateWith.class);
            AnnotationValidator annotationValidator = createAnnotationValidator(validateWithAnnotation);
            errors.addAll(annotationValidator.validateAnnotatedClass(getTestClass().getJavaClass()));
        }
    }

    private void invokeValidatorsOnMethods(List<Throwable> errors) {
        Map<Class<? extends Annotation>, List<FrameworkMethod>> annotationMap = getTestClass().getAnnotationToMethods();
        for (Class<? extends Annotation> annotationType : annotationMap.keySet()) {
            ValidateWith validateWithAnnotation = annotationType.getAnnotation(ValidateWith.class);
            for (FrameworkMethod frameworkMethod : annotationMap.get(annotationType)) {
                AnnotationValidator annotationValidator = createAnnotationValidator(validateWithAnnotation);
                errors.addAll(annotationValidator.validateAnnotatedMethod(frameworkMethod.getMethod()));
            }
        }
    }

    private void invokeValidatorsOnFields(List<Throwable> errors) {
        Map<Class<? extends Annotation>, List<FrameworkField>> annotationMap = getTestClass().getAnnotationToFields();
        for (Class<? extends Annotation> annotationType : annotationMap.keySet()) {
            ValidateWith validateWithAnnotation = annotationType.getAnnotation(ValidateWith.class);
            for (FrameworkField frameworkField : annotationMap.get(annotationType)) {
                AnnotationValidator annotationValidator = createAnnotationValidator(validateWithAnnotation);
                errors.addAll(annotationValidator.validateAnnotatedField(frameworkField.getField()));
            }
        }
    }

    private AnnotationValidator createAnnotationValidator(ValidateWith validateWithAnnotation) {
        if (validateWithAnnotation == null) {
            return new AnnotationValidator() {
            };
        }
        return fAnnotationValidatorFactory.createAnnotationValidator(validateWithAnnotation);
    }

    /**
     * Adds to {@code errors} if any method in this class is annotated with
     * {@code annotation}, but:
     * <ul>
     * <li>is not public, or
     * <li>takes parameters, or
     * <li>returns something other than void, or
     * <li>is static (given {@code isStatic is false}), or
     * <li>is not static (given {@code isStatic is true}).
     */
    protected void validatePublicVoidNoArgMethods(Class<? extends Annotation> annotation,
            boolean isStatic, List<Throwable> errors) {
        List<FrameworkMethod> methods = getTestClass().getAnnotatedMethods(annotation);

        for (FrameworkMethod eachTestMethod : methods) {
            eachTestMethod.validatePublicVoidNoArg(isStatic, errors);
        }
    }

    private void validateClassRules(List<Throwable> errors) {
        CLASS_RULE_VALIDATOR.validate(getTestClass(), errors);
        CLASS_RULE_METHOD_VALIDATOR.validate(getTestClass(), errors);
    }

    /**
     * Constructs a {@code Statement} to run all of the tests in the test class. Override to add pre-/post-processing.
     * Here is an outline of the implementation:
     * <ul>
     * <li>Call {@link #runChild(Object, RunNotifier)} on each object returned by {@link #getChildren()} (subject to any imposed filter and sort).</li>
     * <li>ALWAYS run all non-overridden {@code @BeforeClass} methods on this class
     * and superclasses before the previous step; if any throws an
     * Exception, stop execution and pass the exception on.
     * <li>ALWAYS run all non-overridden {@code @AfterClass} methods on this class
     * and superclasses before any of the previous steps; all AfterClass methods are
     * always executed: exceptions thrown by previous steps are combined, if
     * necessary, with exceptions from AfterClass methods into a
     * {@link MultipleFailureException}.
     * </ul>
     *
     * @return {@code Statement}
     */
    protected Statement classBlock(final RunNotifier notifier) {
        Statement statement = childrenInvoker(notifier);
        statement = withBeforeClasses(statement);
        statement = withAfterClasses(statement);
        statement = withClassRules(statement);
        return statement;
    }

    /**
     * Returns a {@link Statement}: run all non-overridden {@code @BeforeClass} methods on this class
     * and superclasses before executing {@code statement}; if any throws an
     * Exception, stop execution and pass the exception on.
     */
    protected Statement withBeforeClasses(Statement statement) {
        List<FrameworkMethod> befores = fTestClass
                .getAnnotatedMethods(BeforeClass.class);
        return befores.isEmpty() ? statement :
                new RunBefores(statement, befores, null);
    }

    /**
     * Returns a {@link Statement}: run all non-overridden {@code @AfterClass} methods on this class
     * and superclasses before executing {@code statement}; all AfterClass methods are
     * always executed: exceptions thrown by previous steps are combined, if
     * necessary, with exceptions from AfterClass methods into a
     * {@link MultipleFailureException}.
     */
    protected Statement withAfterClasses(Statement statement) {
        List<FrameworkMethod> afters = fTestClass
                .getAnnotatedMethods(AfterClass.class);
        return afters.isEmpty() ? statement :
                new RunAfters(statement, afters, null);
    }

    /**
     * Returns a {@link Statement}: apply all
     * static fields assignable to {@link TestRule}
     * annotated with {@link ClassRule}.
     *
     * @param statement the base statement
     * @return a RunRules statement if any class-level {@link Rule}s are
     *         found, or the base statement
     */
    private Statement withClassRules(Statement statement) {
        List<TestRule> classRules = classRules();
        return classRules.isEmpty() ? statement :
                new RunRules(statement, classRules, getDescription());
    }

    /**
     * @return the {@code ClassRule}s that can transform the block that runs
     *         each method in the tested class.
     */
    protected List<TestRule> classRules() {
        List<TestRule> result = fTestClass.getAnnotatedMethodValues(null, ClassRule.class, TestRule.class);
        result.addAll(fTestClass.getAnnotatedFieldValues(null, ClassRule.class, TestRule.class));
        return result;
    }

    /**
     * Returns a {@link Statement}: Call {@link #runChild(Object, RunNotifier)}
     * on each object returned by {@link #getChildren()} (subject to any imposed
     * filter and sort)
     */
    protected Statement childrenInvoker(final RunNotifier notifier) {
        return new Statement() {
            @Override
            public void evaluate() {
                runChildren(notifier);
            }
        };
    }

    private void runChildren(final RunNotifier notifier) {
        final RunnerScheduler scheduler = fScheduler;
        try {
            for (final T each : getFilteredChildren()) {
                scheduler.schedule(new Runnable() {
                    public void run() {
                        ParentRunner.this.runChild(each, notifier);
                    }
                });
            }
        } finally {
            scheduler.finished();
        }
    }

    /**
     * Returns a name used to describe this Runner
     */
    protected String getName() {
        return fTestClass.getName();
    }

    //
    // Available for subclasses
    //

    /**
     * Returns a {@link TestClass} object wrapping the class to be executed.
     */
    public final TestClass getTestClass() {
        return fTestClass;
    }

    /**
     * Runs a {@link Statement} that represents a leaf (aka atomic) test.
     */
    protected final void runLeaf(Statement statement, Description description,
            RunNotifier notifier) {
        EachTestNotifier eachNotifier = new EachTestNotifier(notifier, description);
        eachNotifier.fireTestStarted();
        try {
            statement.evaluate();
        } catch (AssumptionViolatedException e) {
            eachNotifier.addFailedAssumption(e);
        } catch (Throwable e) {
            eachNotifier.addFailure(e);
        } finally {
            eachNotifier.fireTestFinished();
        }
    }

    /**
     * @return the annotations that should be attached to this runner's
     *         description.
     */
    protected Annotation[] getRunnerAnnotations() {
        return fTestClass.getAnnotations();
    }

    //
    // Implementation of Runner
    //

    @Override
    public Description getDescription() {
        Description description = Description.createSuiteDescription(getName(),
                getRunnerAnnotations());
        for (T child : getFilteredChildren()) {
            description.addChild(describeChild(child));
        }
        return description;
    }

    @Override
    public void run(final RunNotifier notifier) {
        EachTestNotifier testNotifier = new EachTestNotifier(notifier,
                getDescription());
        try {
            Statement statement = classBlock(notifier);
            statement.evaluate();
        } catch (AssumptionViolatedException e) {
            testNotifier.fireTestIgnored();
        } catch (StoppedByUserException e) {
            throw e;
        } catch (Throwable e) {
            testNotifier.addFailure(e);
        }
    }

    //
    // Implementation of Filterable and Sortable
    //

    public void filter(Filter filter) throws NoTestsRemainException {
        synchronized (fChildrenLock) {
            List<T> filteredChildren = new ArrayList<T>(getFilteredChildren());
            for (Iterator<T> iter = filteredChildren.iterator(); iter.hasNext(); ) {
                T each = iter.next();
                if (shouldRun(filter, each)) {
                    try {
                        filter.apply(each);
                    } catch (NoTestsRemainException e) {
                        iter.remove();
                    }
                } else {
                    iter.remove();
                }
            }
            fFilteredChildren = Collections.unmodifiableCollection(filteredChildren);
            if (fFilteredChildren.isEmpty()) {
                throw new NoTestsRemainException();
            }
        }
    }

    public void sort(Sorter sorter) {
        synchronized (fChildrenLock) {
            for (T each : getFilteredChildren()) {
                sorter.apply(each);
            }
            List<T> sortedChildren = new ArrayList<T>(getFilteredChildren());
            Collections.sort(sortedChildren, comparator(sorter));
            fFilteredChildren = Collections.unmodifiableCollection(sortedChildren);
        }
    }

    //
    // Private implementation
    //

    private void validate() throws InitializationError {
        List<Throwable> errors = new ArrayList<Throwable>();
        collectInitializationErrors(errors);
        if (!errors.isEmpty()) {
            throw new InitializationError(errors);
        }
    }

    private Collection<T> getFilteredChildren() {
        if (fFilteredChildren == null) {
            synchronized (fChildrenLock) {
                if (fFilteredChildren == null) {
                    fFilteredChildren = Collections.unmodifiableCollection(getChildren());
                }
            }
        }
        return fFilteredChildren;
    }

    private boolean shouldRun(Filter filter, T each) {
        return filter.shouldRun(describeChild(each));
    }

    private Comparator<? super T> comparator(final Sorter sorter) {
        return new Comparator<T>() {
            public int compare(T o1, T o2) {
                return sorter.compare(describeChild(o1), describeChild(o2));
            }
        };
    }

    /**
     * Sets a scheduler that determines the order and parallelization
     * of children.  Highly experimental feature that may change.
     */
    public void setScheduler(RunnerScheduler scheduler) {
        this.fScheduler = scheduler;
    }
}

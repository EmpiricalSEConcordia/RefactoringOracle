package org.junit.internal.runners;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Assume.AssumptionViolatedException;
import org.junit.internal.runners.links.RunAfters;
import org.junit.internal.runners.links.RunBefores;
import org.junit.internal.runners.links.Statement;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.internal.runners.model.TestClass;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;

public abstract class ParentRunner<T> extends Runner implements Filterable, Sortable {
	protected final TestClass fTestClass;
	private List<T> fChildren = null;
	private Filter fFilter = null;
	private Sorter fSorter = null;

	public ParentRunner(Class<?> testClass) {
		fTestClass = new TestClass(testClass);
	}

	//
	// Must be overridden
	//
	
	protected abstract List<T> getChildren();
	
	protected abstract Description describeChild(T child);

	protected abstract void runChild(T child, RunNotifier notifier);

	//
	// May be overridden
	//
	
	protected Statement runChildren(final RunNotifier notifier) {
		return new Statement() {
			@Override
			public void evaluate() {
				for (T each : getFilteredChildren())
					runChild(each, notifier);
			}
		};
	}

	protected Statement classBlock(final RunNotifier notifier) {
		Statement statement= runChildren(notifier);
		statement= new RunBefores(statement, fTestClass, null);
		statement= new RunAfters(statement, fTestClass, null);
		return statement;
	}

	protected Annotation[] classAnnotations() {
		return fTestClass.getAnnotations();
	}

	protected String getName() {
		return fTestClass.getName();
	}

	//
	// Available for subclasses
	//
	
	protected final TestClass getTestClass() {
		return fTestClass;
	}

	protected final void assertValid(List<Throwable> errors) throws InitializationError {
		if (!errors.isEmpty())
			throw new InitializationError(errors);
	}
	
	//
	// Implementation
	// 
	
	public void filter(Filter filter) throws NoTestsRemainException {
		fFilter= filter;
		
		for (T each : getChildren())
			if (shouldRun(each))
				return;
		throw new NoTestsRemainException();
	}
	
	public void sort(Sorter sorter) {
		fSorter= sorter;
	}

	@Override
	public Description getDescription() {
		Description description= Description.createSuiteDescription(getName(), classAnnotations());
		for (T child : getFilteredChildren())
			description.addChild(describeChild(child));
		return description;
	}
	
	@Override
	public void run(final RunNotifier notifier) {
		EachTestNotifier testNotifier= new EachTestNotifier(notifier,
				getDescription());
		try {
			Statement statement= classBlock(notifier);
			statement.evaluate();
		} catch (AssumptionViolatedException e) {
			testNotifier.addFailedAssumption(e);
		} catch (StoppedByUserException e) {
			throw e;
		} catch (Throwable e) {
			testNotifier.addFailure(e);
		}
	}

	private List<T> getFilteredChildren() {
		if (fChildren == null)
			fChildren = computeFilteredChildren();
		return fChildren;
	}

	private List<T> computeFilteredChildren() {
		ArrayList<T> filtered= new ArrayList<T>();
		for (T each : getChildren())
			if (shouldRun(each)) {
				try {
					filtered.add(sortChild(filterChild(each)));
				} catch (NoTestsRemainException e) {
					// don't add it
				}
			}
		if (fSorter != null)
			Collections.sort(filtered, comparator());
		return filtered;
	}
	
	private T sortChild(T child) {
		if (fSorter != null)
			fSorter.apply(child);
		return child;
	}

	private T filterChild(T child) throws NoTestsRemainException {
		if (fFilter != null)
			fFilter.apply(child);
		return child;
	}

	private boolean shouldRun(T each) {
		return fFilter == null || fFilter.shouldRun(describeChild(each));
	}

	private Comparator<? super T> comparator() {
		return new Comparator<T>() {
			public int compare(T o1, T o2) {
				return fSorter.compare(describeChild(o1), describeChild(o2));
			}
		};
	}
}
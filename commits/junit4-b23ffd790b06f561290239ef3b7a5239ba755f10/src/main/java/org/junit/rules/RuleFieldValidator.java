package org.junit.rules;

import java.lang.annotation.Annotation;
import java.util.List;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.TestClass;

public enum RuleFieldValidator {
	CLASS_RULE_VALIDATOR(ClassRule.class, true), RULE_VALIDATOR(Rule.class,
			false);

	private final Class<? extends Annotation> annotation;

	private final boolean onlyStaticFields;

	private RuleFieldValidator(Class<? extends Annotation> annotation,
			boolean onlyStaticFields) {
		this.annotation= annotation;
		this.onlyStaticFields= onlyStaticFields;
	}

	public void validate(TestClass target, List<Throwable> errors) {
		List<FrameworkField> fields= target.getAnnotatedFields(annotation);
		for (FrameworkField eachField : fields)
			validateField(eachField, errors);
	}

	private void validateField(FrameworkField field, List<Throwable> errors) {
		optionallyValidateStatic(field, errors);
		validatePublic(field, errors);
		validateNotNull(field, errors);
		validateTestRuleOrMethodRule(field, errors);
	}

	private void optionallyValidateStatic(FrameworkField field,
			List<Throwable> errors) {
		if (onlyStaticFields && !field.isStatic())
			addError(errors, field, "must be static.");
	}

	private void validatePublic(FrameworkField field, List<Throwable> errors) {
		if (!field.isPublic())
			addError(errors, field, "must be public.");
	}

	private void validateNotNull(FrameworkField field, List<Throwable> errors) {
		if (field.isStatic() && field.isPublic())
			try {
				Object value= field.get(null);
				if (value == null) {
					addError(errors, field, "must not be null.");
				}
			} catch (IllegalAccessException e) {
				throw new IllegalArgumentException(field.getName()
						+ "'s value is not accessible.", e);
			}
	}

	private void validateTestRuleOrMethodRule(FrameworkField field,
			List<Throwable> errors) {
		// Not tested
		if (!isMethodRule(field) && !isTestRule(field))
			addError(errors, field, "must implement MethodRule or TestRule.");
	}

	private boolean isTestRule(FrameworkField target) {
		return TestRule.class.isAssignableFrom(target.getType());
	}

	@SuppressWarnings("deprecation")
	private boolean isMethodRule(FrameworkField target) {
		return MethodRule.class.isAssignableFrom(target.getType());
	}

	private void addError(List<Throwable> errors, FrameworkField field,
			String suffix) {
		String message= "The " + field.getType().getSimpleName() + " '"
				+ field.getName() + "' " + suffix;
		errors.add(new Exception(message));
	}
}

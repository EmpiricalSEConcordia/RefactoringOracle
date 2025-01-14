package org.junit.tests.validation;

import org.junit.Test;
import org.junit.internal.runners.InitializationError;
import org.junit.internal.runners.MethodValidator;
import org.junit.internal.runners.TestClass;
import org.junit.tests.validation.anotherpackage.Sub;

public class InaccessibleBaseClassTest {	
	@Test(expected=InitializationError.class)
	public void inaccessibleBaseClassIsCaughtAtValidation() throws InitializationError {
		MethodValidator methodValidator= new MethodValidator(new TestClass(Sub.class));
		methodValidator.validateMethodsForDefaultRunner();
		methodValidator.assertValid();
	}
}

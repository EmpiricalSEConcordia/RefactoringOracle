/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.exception;

/**
 * Temporarily extending org.hibernate.annotations.common.AssertionFailure
 * for backwards compatibility. The parent class is going to be removed!
 */
public class AssertionFailure extends org.hibernate.annotations.common.AssertionFailure {

	public AssertionFailure(String s, Throwable t) {
		super( s, t );
	}

	public AssertionFailure(String s) {
		super( s );
	}

}

/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.search.metadata.impl;

import org.hibernate.search.engine.metadata.impl.DocumentFieldMetadata;
import org.hibernate.search.metadata.NumericFieldSettingsDescriptor;

/**
 * @author Hardy Ferentschik
 */
public class NumericFieldDescriptorImpl extends FieldDescriptorImpl implements NumericFieldSettingsDescriptor {
	private final int precisionStep;

	public NumericFieldDescriptorImpl(DocumentFieldMetadata documentFieldMetadata) {
		super( documentFieldMetadata );
		this.precisionStep = documentFieldMetadata.getPrecisionStep();
	}

	@Override
	public int precisionStep() {
		return precisionStep;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder( "NumericFieldDescriptorImpl{" );
		sb.append( "precisionStep=" ).append( precisionStep );
		sb.append( '}' );
		return sb.toString();
	}
}

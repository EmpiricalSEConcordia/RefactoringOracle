/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010 by Red Hat Inc and/or its affiliates or by
 * third-party contributors as indicated by either @author tags or express
 * copyright attribution statements applied by the authors.  All
 * third-party contributions are distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.relational;


/**
 * Models a compound value (a tuple or row-value-constructor is SQL terms).  It is both a {@link Value} and
 * a {@link ValueContainer} simultaneously.
 * <p/>
 * IMPL NOTE : in terms of the tables themselves, SQL has no notion of a tuple/compound-value.  We simply model
 * it this way because:
 * <ul>
 * <li>it is a cleaner mapping to the logical model</li>
 * <li>it allows more meaningful traversals from simple values back up to table through any intermediate tuples
 * because it gives us a better understanding of the model.</li>
 * <li>it better conveys intent</li>
 * <li>it adds richness to the model</li>
 * </ul>
 *
 * @author Steve Ebersole
 */
public class Tuple extends AbstractValueContainer implements Value {
	private final ValueContainer valueContainer;
	private final String name;

	public Tuple(ValueContainer valueContainer, String name) {
		this.name = name;
		this.valueContainer = valueContainer;
	}

	@Override
	public ValueContainer getValueContainer() {
		return valueContainer;
	}

	@Override
	public String getLoggableValueQualifier() {
		return getValueContainer().getLoggableValueQualifier() + '.' + name;
	}

	@Override
	public String toLoggableString() {
		return getLoggableValueQualifier() + "{tuple}";
	}
}

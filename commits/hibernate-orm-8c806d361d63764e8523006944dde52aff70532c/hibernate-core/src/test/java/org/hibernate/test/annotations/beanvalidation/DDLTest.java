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

package org.hibernate.test.annotations.beanvalidation;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.test.annotations.TestCase;

/**
 * Test verifying that DDL constraints get applied when Bean Validation / Hibernate Validator are enabled.
 *
 * @author Emmanuel Bernard
 * @author Hardy Ferentschik
 */
public class DDLTest extends TestCase {

	public void testBasicDDL() {
		PersistentClass classMapping = getCfg().getClassMapping( Address.class.getName() );
		Column stateColumn = (Column) classMapping.getProperty( "state" ).getColumnIterator().next();
		assertEquals( stateColumn.getLength(), 3 );
		Column zipColumn = (Column) classMapping.getProperty( "zip" ).getColumnIterator().next();
		assertEquals( zipColumn.getLength(), 5 );
		assertFalse( zipColumn.isNullable() );
	}

	public void testApplyOnIdColumn() throws Exception {
		PersistentClass classMapping = getCfg().getClassMapping( Tv.class.getName() );
		Column serialColumn = (Column) classMapping.getIdentifierProperty().getColumnIterator().next();
		assertEquals( "Validator annotation not applied on ids", 2, serialColumn.getLength() );
	}

	/**
	 * HHH-5281
	 *
	 * @throws Exception in case the test fails
	 */
	public void testLengthConstraint() throws Exception {
		PersistentClass classMapping = getCfg().getClassMapping( Tv.class.getName() );
		Column modelColumn = (Column) classMapping.getProperty( "model" ).getColumnIterator().next();
		assertEquals( modelColumn.getLength(), 5 );
	}

	public void testApplyOnManyToOne() throws Exception {
		PersistentClass classMapping = getCfg().getClassMapping( TvOwner.class.getName() );
		Column serialColumn = (Column) classMapping.getProperty( "tv" ).getColumnIterator().next();
		assertEquals( "Validator annotations not applied on associations", false, serialColumn.isNullable() );
	}

	public void testSingleTableAvoidNotNull() throws Exception {
		PersistentClass classMapping = getCfg().getClassMapping( Rock.class.getName() );
		Column serialColumn = (Column) classMapping.getProperty( "bit" ).getColumnIterator().next();
		assertTrue( "Notnull should not be applied on single tables", serialColumn.isNullable() );
	}

	public void testNotNullOnlyAppliedIfEmbeddedIsNotNullItself() throws Exception {
		PersistentClass classMapping = getCfg().getClassMapping( Tv.class.getName() );
		Property property = classMapping.getProperty( "tuner.frequency" );
		Column serialColumn = (Column) property.getColumnIterator().next();
		assertEquals(
				"Validator annotations are applied on tuner as it is @NotNull", false, serialColumn.isNullable()
		);

		property = classMapping.getProperty( "recorder.time" );
		serialColumn = (Column) property.getColumnIterator().next();
		assertEquals(
				"Validator annotations are applied on tuner as it is @NotNull", true, serialColumn.isNullable()
		);
	}

	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
				Address.class,
				Tv.class,
				TvOwner.class,
				Rock.class
		};
	}
}

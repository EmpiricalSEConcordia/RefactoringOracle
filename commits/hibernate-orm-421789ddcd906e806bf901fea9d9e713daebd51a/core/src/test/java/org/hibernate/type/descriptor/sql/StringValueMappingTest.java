/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
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
package org.hibernate.type.descriptor.sql;

import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import junit.framework.TestCase;

import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.jdbc.NonContextualLobCreator;
import org.hibernate.type.descriptor.java.StringTypeDescriptor;
import org.hibernate.type.descriptor.java.WrapperOptions;
import org.hibernate.type.descriptor.sql.ClobTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

/**
 * TODO : javadoc
 *
 * @author Steve Ebersole
 */
public class StringValueMappingTest extends TestCase {
	private final StringTypeDescriptor stringJavaDescriptor = new StringTypeDescriptor();

	private final VarcharTypeDescriptor varcharSqlDescriptor = new VarcharTypeDescriptor();
	private final ClobTypeDescriptor clobSqlDescriptor = new ClobTypeDescriptor();

	private final WrapperOptions wrapperOptions = new WrapperOptions() {
		public boolean useStreamForLobBinding() {
			return false;
		}

		public LobCreator getLobCreator() {
			return NonContextualLobCreator.INSTANCE;
		}
	};

	public static final String COLUMN_NAME = "n/a";
	public static final int BIND_POSITION = -1;

	public void testNormalVarcharHandling() throws SQLException {
		final SqlTypeDescriptor.Extractor<String> extractor = varcharSqlDescriptor.getExtractor( stringJavaDescriptor );
		final SqlTypeDescriptor.Binder<String> binder = varcharSqlDescriptor.getBinder( stringJavaDescriptor );

		final String fixture = "string value";

		ResultSet resultSet = ResultSetProxy.generateProxy( fixture );
		final String value = extractor.extract( resultSet, COLUMN_NAME, wrapperOptions );
		assertEquals( fixture, value );

		PreparedStatement ps = PreparedStatementProxy.generateProxy( fixture );
		binder.bind( ps, fixture, BIND_POSITION, wrapperOptions );
	}

	public void testNullVarcharHandling() throws SQLException {
		final SqlTypeDescriptor.Extractor<String> extractor = varcharSqlDescriptor.getExtractor( stringJavaDescriptor );
		final SqlTypeDescriptor.Binder<String> binder = varcharSqlDescriptor.getBinder( stringJavaDescriptor );

		final String fixture = null;

		ResultSet resultSet = ResultSetProxy.generateProxy( fixture );
		final String value = extractor.extract( resultSet, COLUMN_NAME, wrapperOptions );
		assertEquals( fixture, value );

		PreparedStatement ps = PreparedStatementProxy.generateProxy( fixture );
		binder.bind( ps, fixture, BIND_POSITION, wrapperOptions );
	}

	public void testNormalClobHandling() throws SQLException {
		final SqlTypeDescriptor.Extractor<String> extractor = clobSqlDescriptor.getExtractor( stringJavaDescriptor );
		final SqlTypeDescriptor.Binder<String> binder = clobSqlDescriptor.getBinder( stringJavaDescriptor );

		final String fixture = "clob string";
		final Clob clob = new StringClobImpl( fixture );

		ResultSet resultSet = ResultSetProxy.generateProxy( clob );
		final String value = extractor.extract( resultSet, COLUMN_NAME, wrapperOptions );
		assertEquals( fixture, value );

		PreparedStatement ps = PreparedStatementProxy.generateProxy( clob );
		binder.bind( ps, fixture, BIND_POSITION, wrapperOptions );
	}

	public void testNullClobHandling() throws SQLException {
		final SqlTypeDescriptor.Extractor<String> extractor = clobSqlDescriptor.getExtractor( stringJavaDescriptor );
		final SqlTypeDescriptor.Binder<String> binder = clobSqlDescriptor.getBinder( stringJavaDescriptor );

		final String fixture = null;
		final Clob clob = null;

		ResultSet resultSet = ResultSetProxy.generateProxy( clob );
		final String value = extractor.extract( resultSet, COLUMN_NAME, wrapperOptions );
		assertNull( value );

		PreparedStatement ps = PreparedStatementProxy.generateProxy( clob );
		binder.bind( ps, fixture, BIND_POSITION, wrapperOptions );
	}
}

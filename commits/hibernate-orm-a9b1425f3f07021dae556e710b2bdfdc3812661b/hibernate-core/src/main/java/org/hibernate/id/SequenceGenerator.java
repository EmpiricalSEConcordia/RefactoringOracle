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
package org.hibernate.id;

import static org.jboss.logging.Logger.Level.DEBUG;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.cfg.ObjectNameNormalizer;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.exception.JDBCExceptionHelper;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.mapping.Table;
import org.hibernate.type.Type;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.LogMessage;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;

/**
 * <b>sequence</b><br>
 * <br>
 * Generates <tt>long</tt> values using an oracle-style sequence. A higher
 * performance algorithm is <tt>SequenceHiLoGenerator</tt>.<br>
 * <br>
 * Mapping parameters supported: sequence, parameters.
 *
 * @see SequenceHiLoGenerator
 * @see TableHiLoGenerator
 * @author Gavin King
 */
public class SequenceGenerator implements PersistentIdentifierGenerator, Configurable {

    private static final Logger LOG = org.jboss.logging.Logger.getMessageLogger(Logger.class,
                                                                                SequenceGenerator.class.getPackage().getName());

	/**
	 * The sequence parameter
	 */
	public static final String SEQUENCE = "sequence";

	/**
	 * The parameters parameter, appended to the create sequence DDL.
	 * For example (Oracle): <tt>INCREMENT BY 1 START WITH 1 MAXVALUE 100 NOCACHE</tt>.
	 */
	public static final String PARAMETERS = "parameters";

	private String sequenceName;
	private String parameters;
	private Type identifierType;
	private String sql;

	protected Type getIdentifierType() {
		return identifierType;
	}

	public void configure(Type type, Properties params, Dialect dialect) throws MappingException {
		ObjectNameNormalizer normalizer = ( ObjectNameNormalizer ) params.get( IDENTIFIER_NORMALIZER );
		sequenceName = normalizer.normalizeIdentifierQuoting(
				ConfigurationHelper.getString( SEQUENCE, params, "hibernate_sequence" )
		);
		parameters = params.getProperty( PARAMETERS );

		if ( sequenceName.indexOf( '.' ) < 0 ) {
			final String schemaName = normalizer.normalizeIdentifierQuoting( params.getProperty( SCHEMA ) );
			final String catalogName = normalizer.normalizeIdentifierQuoting( params.getProperty( CATALOG ) );
			sequenceName = Table.qualify(
					dialect.quote( catalogName ),
					dialect.quote( schemaName ),
					dialect.quote( sequenceName )
			);
		}
		else {
			// if already qualified there is not much we can do in a portable manner so we pass it
			// through and assume the user has set up the name correctly.
		}

		this.identifierType = type;
		sql = dialect.getSequenceNextValString( sequenceName );
	}

	public Serializable generate(SessionImplementor session, Object obj) {
		return generateHolder( session ).makeValue();
	}

	protected IntegralDataTypeHolder generateHolder(SessionImplementor session) {
		try {
			PreparedStatement st = session.getJDBCContext().getConnectionManager().prepareSelectStatement( sql );
			try {
				ResultSet rs = st.executeQuery();
				try {
					rs.next();
					IntegralDataTypeHolder result = buildHolder();
					result.initialize( rs, 1 );
                    LOG.sequenceIdentifierGenerated(result);
					return result;
				}
				finally {
					rs.close();
				}
			}
			finally {
				st.close();
			}

		}
		catch (SQLException sqle) {
			throw session.getFactory().getSQLExceptionHelper().convert(
					sqle,
					"could not get next sequence value",
					sql
			);
		}
	}

	protected IntegralDataTypeHolder buildHolder() {
		return IdentifierGeneratorHelper.getIntegralDataTypeHolder( identifierType.getReturnedClass() );
	}

	public String[] sqlCreateStrings(Dialect dialect) throws HibernateException {
		String[] ddl = dialect.getCreateSequenceStrings(sequenceName);
		if ( parameters != null ) {
			ddl[ddl.length - 1] += ' ' + parameters;
		}
		return ddl;
	}

	public String[] sqlDropStrings(Dialect dialect) throws HibernateException {
		return dialect.getDropSequenceStrings(sequenceName);
	}

	public Object generatorKey() {
		return sequenceName;
	}

	public String getSequenceName() {
		return sequenceName;
	}

    /**
     * Interface defining messages that may be logged by the outer class
     */
    @MessageLogger
    interface Logger extends BasicLogger {

        @LogMessage( level = DEBUG )
        @Message( value = "Sequence identifier generated: %s" )
        void sequenceIdentifierGenerated( IntegralDataTypeHolder result );
    }
}

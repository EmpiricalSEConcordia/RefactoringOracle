/*
 * Copyright 2002-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.orm.ibatis.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;

/**
 * iBATIS TypeHandler implementation for arbitrary objects that get serialized to BLOBs.
 * Retrieves the LobHandler to use from SqlMapClientFactoryBean at config time.
 *
 * <p>Can also be defined in generic iBATIS mappings, as DefaultLobCreator will
 * work with most JDBC-compliant database drivers. In this case, the field type
 * does not have to be BLOB: For databases like MySQL and MS SQL Server, any
 * large enough binary type will work.
 *
 * @author Juergen Hoeller
 * @since 1.1.5
 * @see org.springframework.orm.ibatis.SqlMapClientFactoryBean#setLobHandler
 * @deprecated as of Spring 3.2, in favor of the native Spring support
 * in the Mybatis follow-up project (http://code.google.com/p/mybatis/)
 */
@Deprecated
public class BlobSerializableTypeHandler extends AbstractLobTypeHandler {

	/**
	 * Constructor used by iBATIS: fetches config-time LobHandler from
	 * SqlMapClientFactoryBean.
	 * @see org.springframework.orm.ibatis.SqlMapClientFactoryBean#getConfigTimeLobHandler
	 */
	public BlobSerializableTypeHandler() {
		super();
	}

	/**
	 * Constructor used for testing: takes an explicit LobHandler.
	 */
	protected BlobSerializableTypeHandler(LobHandler lobHandler) {
		super(lobHandler);
	}

	@Override
	protected void setParameterInternal(
			PreparedStatement ps, int index, Object value, String jdbcType, LobCreator lobCreator)
			throws SQLException, IOException {

		if (value != null) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			try {
				oos.writeObject(value);
				oos.flush();
				lobCreator.setBlobAsBytes(ps, index, baos.toByteArray());
			}
			finally {
				oos.close();
			}
		}
		else {
			lobCreator.setBlobAsBytes(ps, index, null);
		}
	}

	@Override
	protected Object getResultInternal(ResultSet rs, int index, LobHandler lobHandler)
			throws SQLException, IOException {

		InputStream is = lobHandler.getBlobAsBinaryStream(rs, index);
		if (is != null) {
			ObjectInputStream ois = new ObjectInputStream(is);
			try {
				return ois.readObject();
			}
			catch (ClassNotFoundException ex) {
				throw new SQLException("Could not deserialize BLOB contents: " + ex.getMessage());
			}
			finally {
				ois.close();
			}
		}
		else {
			return null;
		}
	}

	@Override
	public Object valueOf(String s) {
		return s;
	}

}

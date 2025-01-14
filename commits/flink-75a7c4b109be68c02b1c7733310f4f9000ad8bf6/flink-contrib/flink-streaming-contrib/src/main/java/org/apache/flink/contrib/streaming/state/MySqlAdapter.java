/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.contrib.streaming.state;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.flink.api.java.tuple.Tuple2;

/**
 * 
 * Adapter for bridging inconsistencies between the different SQL
 * implementations. The default implementation has been tested to work well with
 * MySQL
 *
 */
public class MySqlAdapter implements Serializable, DbAdapter {

	private static final long serialVersionUID = 1L;

	// -----------------------------------------------------------------------------
	// Non-partitioned state checkpointing
	// -----------------------------------------------------------------------------

	@Override
	public void createCheckpointsTable(String jobId, Connection con) throws SQLException {
		try (Statement smt = con.createStatement()) {
			smt.executeUpdate(
					"CREATE TABLE IF NOT EXISTS checkpoints_" + jobId
							+ " ("
							+ "checkpointId bigint, "
							+ "timestamp bigint, "
							+ "handleId bigint,"
							+ "checkpoint blob,"
							+ "PRIMARY KEY (handleId)"
							+ ")");
		}

	}

	@Override
	public PreparedStatement prepareCheckpointInsert(String jobId, Connection con) throws SQLException {
		return con.prepareStatement(
				"INSERT INTO checkpoints_" + jobId
						+ " (checkpointId, timestamp, handleId, checkpoint) VALUES (?,?,?,?)");
	}

	@Override
	public void setCheckpointInsertParams(String jobId, PreparedStatement insertStatement, long checkpointId,
			long timestamp, long handleId, byte[] checkpoint) throws SQLException {
		insertStatement.setLong(1, checkpointId);
		insertStatement.setLong(2, timestamp);
		insertStatement.setLong(3, handleId);
		insertStatement.setBytes(4, checkpoint);
	}

	@Override
	public byte[] getCheckpoint(String jobId, Connection con, long checkpointId, long checkpointTs, long handleId)
			throws SQLException {
		try (Statement smt = con.createStatement()) {
			ResultSet rs = smt.executeQuery(
					"SELECT checkpoint FROM checkpoints_" + jobId
							+ " WHERE handleId = " + handleId);
			if (rs.next()) {
				return rs.getBytes(1);
			} else {
				throw new SQLException("Checkpoint cannot be found in the database.");
			}
		}
	}

	@Override
	public void deleteCheckpoint(String jobId, Connection con, long checkpointId, long checkpointTs, long handleId)
			throws SQLException {
		try (Statement smt = con.createStatement()) {
			smt.executeUpdate(
					"DELETE FROM checkpoints_" + jobId
							+ " WHERE handleId = " + handleId);
		}
	}

	@Override
	public void disposeAllStateForJob(String jobId, Connection con) throws SQLException {
		try (Statement smt = con.createStatement()) {
			smt.executeUpdate(
					"DROP TABLE checkpoints_" + jobId);
		}
	}

	// -----------------------------------------------------------------------------
	// Partitioned state checkpointing
	// -----------------------------------------------------------------------------

	@Override
	public void createKVStateTable(String stateId, Connection con) throws SQLException {
		validateStateId(stateId);
		try (Statement smt = con.createStatement()) {
			smt.executeUpdate(
					"CREATE TABLE IF NOT EXISTS kvstate_" + stateId
							+ " ("
							+ "id bigint, "
							+ "k varbinary(256), "
							+ "v blob, "
							+ "PRIMARY KEY (k, id) "
							+ ")");
		}
	}

	@Override
	public PreparedStatement prepareKVCheckpointInsert(String stateId, Connection con) throws SQLException {
		validateStateId(stateId);
		return con.prepareStatement(
				"INSERT INTO kvstate_" + stateId + " (id, k, v) VALUES (?,?,?) "
						+ "ON DUPLICATE KEY UPDATE v=? ");
	}

	@Override
	public PreparedStatement prepareKeyLookup(String stateId, Connection con) throws SQLException {
		validateStateId(stateId);
		return con.prepareStatement("SELECT v"
				+ " FROM kvstate_" + stateId
				+ " WHERE k = ?"
				+ " AND id <= ?"
				+ " ORDER BY id DESC LIMIT 1");
	}

	@Override
	public byte[] lookupKey(String stateId, PreparedStatement lookupStatement, byte[] key, long lookupId)
			throws SQLException {
		lookupStatement.setBytes(1, key);
		lookupStatement.setLong(2, lookupId);

		ResultSet res = lookupStatement.executeQuery();

		if (res.next()) {
			return res.getBytes(1);
		} else {
			return null;
		}
	}

	@Override
	public void cleanupFailedCheckpoints(String stateId, Connection con, long checkpointId,
			long nextId) throws SQLException {
		validateStateId(stateId);
		try (Statement smt = con.createStatement()) {
			smt.executeUpdate("DELETE FROM kvstate_" + stateId
					+ " WHERE id > " + checkpointId
					+ " AND id < " + nextId);
			System.out.println("Cleaned up");
		}
	}

	protected void compactKvStates(String stateId, Connection con, long lowerId, long upperId)
			throws SQLException {
		validateStateId(stateId);

		try (Statement smt = con.createStatement()) {
			smt.executeUpdate("DELETE state.* FROM kvstate_" + stateId + " AS state"
					+ " JOIN"
					+ " ("
					+ " 	SELECT MAX(id) AS maxts, k FROM kvstate_" + stateId
					+ " 	WHERE id BETWEEN " + lowerId + " AND " + upperId
					+ " 	GROUP BY k"
					+ " ) m"
					+ " ON state.k = m.k"
					+ " AND state.id >= " + lowerId);
			System.out.println("Compacted");
		}
	}

	/**
	 * Tries to avoid SQL injection with weird state names.
	 * 
	 */
	protected static void validateStateId(String name) {
		if (!name.matches("[a-zA-Z0-9_]+")) {
			throw new RuntimeException("State name contains invalid characters.");
		}
	}

	@Override
	public void insertBatch(final String stateId, final DbBackendConfig conf,
			final Connection con, final PreparedStatement insertStatement, final long checkpointId,
			final List<Tuple2<byte[], byte[]>> toInsert) throws IOException {

		SQLRetrier.retry(new Callable<Void>() {
			public Void call() throws Exception {
				for (Tuple2<byte[], byte[]> kv : toInsert) {
					setKvInsertParams(stateId, insertStatement, checkpointId, kv.f0, kv.f1);
					insertStatement.addBatch();
				}
				insertStatement.executeBatch();
				insertStatement.clearBatch();
				System.out.println("Batch inserted");
				return null;
			}
		}, new Callable<Void>() {
			public Void call() throws Exception {
				insertStatement.clearBatch();
				return null;
			}
		}, conf.getMaxNumberOfSqlRetries(), conf.getSleepBetweenSqlRetries());
	}

	private void setKvInsertParams(String stateId, PreparedStatement insertStatement, long checkpointId,
			byte[] key, byte[] value) throws SQLException {
		insertStatement.setLong(1, checkpointId);
		insertStatement.setBytes(2, key);
		if (value != null) {
			insertStatement.setBytes(3, value);
			insertStatement.setBytes(4, value);
		} else {
			insertStatement.setNull(3, Types.BLOB);
			insertStatement.setNull(4, Types.BLOB);
		}
	}

}

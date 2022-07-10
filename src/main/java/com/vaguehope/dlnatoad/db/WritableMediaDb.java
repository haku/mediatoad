package com.vaguehope.dlnatoad.db;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WritableMediaDb implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(WritableMediaDb.class);

	private final Connection conn;

	protected WritableMediaDb(final Connection conn) throws SQLException {
		if (conn.getAutoCommit()) {
			throw new IllegalArgumentException("AutoCommit must not be enabled.");
		}
		this.conn = conn;
	}

	@Override
	public void close() throws IOException {
		boolean committed = false;
		try {
			commitOrRollback();
			committed = true;
		}
		finally {
			try {
				this.conn.close();
			}
			catch (final SQLException e) {
				if (committed) throw new IOException("Failed to close DB connection.", e);
				LOG.error("Failed to close DB connection", e);
			}
		}
	}

	private void commitOrRollback() throws IOException {
		try {
			this.conn.commit();
		}
		catch (final SQLException e) {
			try {
				this.conn.rollback();
			}
			catch (final SQLException e1) {
				LOG.error("Failed to rollback transaction.", e1);
			}
			throw new IOException("Failed to commit.", e);
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	protected void storeDurations(final List<FileAndDuration> toStore) throws SQLException {
		final List<FileAndDuration> toInsert = new ArrayList<>();

		final PreparedStatement stUpdate = this.conn.prepareStatement(
				"UPDATE durations SET size=?,duration=? WHERE key=?;");
		try {
			for (final FileAndDuration fad : toStore) {
				stUpdate.setLong(1, fad.getFile().length());
				stUpdate.setLong(2, fad.getDuration());
				stUpdate.setString(3, fad.getFile().getAbsolutePath());
				stUpdate.addBatch();
			}
			final int[] nUpdated = stUpdate.executeBatch();

			for (int i = 0; i < nUpdated.length; i++) {
				if (nUpdated[i] < 1) {
					toInsert.add(toStore.get(i));
				}
			}
		}
		finally {
			stUpdate.close();
		}

		final PreparedStatement stInsert = this.conn.prepareStatement(
				"INSERT INTO durations (key,size,duration) VALUES (?,?,?);");
		try {
			for (final FileAndDuration fad : toInsert) {
				stInsert.setString(1, fad.getFile().getAbsolutePath());
				stInsert.setLong(2, fad.getFile().length());
				stInsert.setLong(3, fad.getDuration());
				stInsert.addBatch();
			}
			final int[] nInserted = stInsert.executeBatch();

			for (int i = 0; i < nInserted.length; i++) {
				if (nInserted[i] < 1) {
					LOG.error("No insert occured inserting key '{}'.", toInsert.get(i).getFile().getAbsolutePath());
				}
			}
		}
		finally {
			stInsert.close();
		}
	}

}
package com.vaguehope.dlnatoad.db;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
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

	// The read methods are here so they are reading from the same transaction as the writes around them.

	protected FileData readFileData (final File file) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"SELECT size, modified, hash, id FROM files WHERE file=?;");
		try {
			st.setString(1, file.getAbsolutePath());
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return null;
				final FileData fileData = new FileData(
						rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4));
				if (rs.next()) throw new SQLException("Query for file '" + file.getAbsolutePath() + "' retured more than one result.");
				return fileData;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	protected Collection<FileAndData> filesWithHash (final String hash) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"SELECT file, size, modified, hash, id FROM files WHERE hash=?;");
		try {
			st.setString(1, hash);
			final ResultSet rs = st.executeQuery();
			try {
				final Collection<FileAndData> ret = new ArrayList<>();
				while (rs.next()) {
					ret.add(new FileAndData(
							new File(rs.getString(1)),
							new FileData(rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5))));
				}
				return ret;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	protected String canonicalIdForHash (final String hash) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"SELECT id FROM hashes WHERE hash=?;");
		try {
			st.setString(1, hash);
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return null;
				final String id = rs.getString(1);
				if (rs.next()) throw new SQLException("Query for hash '" + hash + "' retured more than one result.");
				return id;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	protected Collection<String> hashesForId (final String id) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"SELECT hash FROM hashes WHERE id=?;");
		try {
			st.setString(1, id);
			final ResultSet rs = st.executeQuery();
			try {
				final Collection<String> ret = new ArrayList<>();
				while(rs.next()) {
					ret.add(rs.getString(1));
				}
				return ret;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	protected void storeFileData (final File file, final FileData fileData) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"INSERT INTO files (file,size,modified,hash,id) VALUES (?,?,?,?,?);");
		try {
			st.setString(1, file.getAbsolutePath());
			st.setLong(2, fileData.getSize());
			st.setLong(3, fileData.getModified());
			st.setString(4, fileData.getHash());
			st.setString(5, fileData.getId());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No insert occured inserting file '" + file.getAbsolutePath() + "'.");
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to store new data for file %s \"%s\".", file, fileData), e);
		}
		finally {
			st.close();
		}
	}

	protected void updateFileData (final File file, final FileData fileData) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"UPDATE files SET size=?,modified=?,hash=?,id=? WHERE file=?;");
		try {
			st.setLong(1, fileData.getSize());
			st.setLong(2, fileData.getModified());
			st.setString(3, fileData.getHash());
			st.setString(4, fileData.getId());
			st.setString(5, file.getAbsolutePath());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured updating file '" + file.getAbsolutePath() + "'.");
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to update data for file %s to \"%s\".", file, fileData), e);
		}
		finally {
			st.close();
		}
	}

	protected void removeFile (final File file) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"DELETE FROM files WHERE file=?;");
		try {
			st.setString(1, file.getAbsolutePath());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured removing file '" + file.getAbsolutePath() + "'.");
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to remove file \"%s\".", file), e);
		}
		finally {
			st.close();
		}
	}

	protected void storeCanonicalId (final String hash, final String id) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"INSERT INTO hashes (hash,id) VALUES (?,?);");
		try {
			st.setString(1, hash);
			st.setString(2, id);
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured inserting hash '" + hash + "'.");
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to store canonical ID for hash %s \"%s\".", hash, id), e);
		}
		finally {
			st.close();
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
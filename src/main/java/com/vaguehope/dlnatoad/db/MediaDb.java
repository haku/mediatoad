package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Encoding;
import org.sqlite.SQLiteConfig.TransactionMode;

public class MediaDb {

	private final String dbPath;
	private final Connection dbConn;

	public MediaDb (final File dbFile) throws SQLException {
		this("jdbc:sqlite:" + dbFile.getAbsolutePath(), false);
	}

	public MediaDb (final String dbPath) throws SQLException {
		this("jdbc:sqlite:" + dbPath, false);
	}

	private MediaDb (final String dbPath, final boolean ignored) throws SQLException {
		this.dbPath = dbPath;
		this.dbConn = makeDbConnection(dbPath);
		makeSchema();
	}

	private void makeSchema () throws SQLException {
		if (!tableExists("files")) {
			executeSql("CREATE TABLE files ("
					+ "file STRING NOT NULL PRIMARY KEY, size INT NOT NULL, modified INT NOT NULL, hash STRING NOT NULL, id STRING NOT NULL);");
		}
		if (!tableExists("hashes")) {
			executeSql("CREATE TABLE hashes ("
					+ "hash STRING NOT NULL PRIMARY KEY, id STRING NOT NULL);");
			executeSql("CREATE INDEX hashes_idx ON hashes (id);");
		}
		if (!tableExists("durations")) {
			executeSql("CREATE TABLE durations ("
					+ "key STRING NOT NULL PRIMARY KEY, size INT NOT NULL, duration INT NOT NULL);");
		}
	}

	@SuppressWarnings("resource")
	protected WritableMediaDb getWritable() throws SQLException {
		final Connection c = makeDbConnection(this.dbPath);
		c.setAutoCommit(false);
		return new WritableMediaDb(c);
	}

	protected FileData readFileData (final File file) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
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
		final PreparedStatement st = this.dbConn.prepareStatement(
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

	protected void storeFileData (final File file, final FileData fileData) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
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
		final PreparedStatement st = this.dbConn.prepareStatement(
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
		final PreparedStatement st = this.dbConn.prepareStatement(
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

	protected String canonicalIdForHash (final String hash) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
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

	protected void storeCanonicalId (final String hash, final String id) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
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

	protected Collection<String> hashesForId (final String id) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
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

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	protected long readDurationCheckingFileSize (final String key, final long expectedSize) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"SELECT size, duration FROM durations WHERE key=?;");
		try {
			st.setString(1, key);
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return 0;

				final long storedSize = rs.getLong(1);
				final long duration = rs.getLong(2);

				if (rs.next()) throw new SQLException("Query for key '" + key + "' retured more than one result.");

				if (expectedSize != storedSize) return 0;
				return duration;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private static SQLiteConfig makeDbConfig() throws SQLException {
		final SQLiteConfig c = new SQLiteConfig();
		c.setEncoding(Encoding.UTF8);
		c.setSharedCache(true);
		c.setTransactionMode(TransactionMode.DEFERRED);
		return c;
	}

	private static Connection makeDbConnection (final String dbPath) throws SQLException {
		return DriverManager.getConnection(dbPath, makeDbConfig().toProperties());
	}

	private boolean tableExists (final String tableName) throws SQLException {
		final Statement st = this.dbConn.createStatement();
		try {
			final ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "';");
			try {
				return rs.next();
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	private boolean executeSql (final String sql) throws SQLException {
		final Statement st = this.dbConn.createStatement();
		try {
			return st.executeUpdate(sql) > 0;
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to execute SQL \"%s\".", sql), e);
		}
		finally {
			st.close();
		}
	}

}

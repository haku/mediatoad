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

	public static final String COL_FILE = "file";
	public static final String COL_TAG = "tag";

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
					+ COL_FILE + " STRING NOT NULL PRIMARY KEY, size INT NOT NULL, modified INT NOT NULL, hash STRING NOT NULL, id STRING NOT NULL);");
		}
		if (!tableExists("tags")) {
			executeSql("CREATE TABLE tags ("
					+ "id INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ "file_id STRING, "
					+ COL_TAG + " STRING NOT NULL COLLATE NOCASE, "
					+ "modified INT NOT NULL, deleted INT(1), "
					+ "FOREIGN KEY(file_id) REFERENCES files(id) ON DELETE RESTRICT ON UPDATE RESTRICT"
					+ ");");
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
	// Tags.

	protected Collection<Tag> getTags(final String fileId, final boolean includeDeleted) throws SQLException {
		return getTagsFromConn(this.dbConn, fileId, includeDeleted);
	}

	protected static Collection<Tag> getTagFromConn(final Connection conn, final String fileId, final String tag) throws SQLException {
		try (final PreparedStatement st = conn.prepareStatement(SELECT_FROM_TAGS + "file_id=? AND tag=?")) {
			st.setString(1, fileId);
			st.setString(2, tag);
			return readTagsResultSet(st);
		}
	}

	protected static Collection<Tag> getTagsFromConn(final Connection conn, final String fileId, final boolean includeDeleted) throws SQLException {
		String query = SELECT_FROM_TAGS + "file_id=?";
		if (!includeDeleted) query += " AND deleted IS NULL OR deleted=0";
		try (final PreparedStatement st = conn.prepareStatement(query)) {
			st.setString(1, fileId);
			return readTagsResultSet(st);
		}
	}

	private static final String SELECT_FROM_TAGS = "SELECT id,tag,modified,deleted FROM tags WHERE ";

	private static Collection<Tag> readTagsResultSet(final PreparedStatement st) throws SQLException {
		try (final ResultSet rs = st.executeQuery()) {
			final Collection<Tag> ret = new ArrayList<>();
			while (rs.next()) {
				ret.add(new Tag(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getInt(4) > 0));
			}
			return ret;
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

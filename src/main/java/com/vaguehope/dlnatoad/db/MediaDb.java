package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Encoding;

public class MediaDb {

	private static final Logger LOG = LoggerFactory.getLogger(MediaDb.class);

	private final Connection dbConn;

	public MediaDb (final File dbFile) throws SQLException {
		this.dbConn = makeDbConnection(dbFile);
		makeSchema();
	}

	public String idForFile (final File file) throws SQLException, IOException {
		if (!file.isFile()) throw new IOException("Not a file: " + file.getAbsolutePath());

		String id = null;

		FileData fileData = readFileData(file);
		if (fileData == null) {
			fileData = FileData.forFile(file);
			storeFileData(file, fileData);

			id = readId(fileData.getHash());
		}
		else if (!fileData.upToDate(file)) {
			final String oldHash = fileData.getHash();
			fileData = FileData.forFile(file);

			id = readId(oldHash);
			if (id != null && !oldHash.equals(fileData.getHash())) {
				storeId(fileData.getHash(), id);
			}
			updateFileData(file, fileData);
		}
		else {
			id = readId(fileData.getHash());
		}

		if (id == null) {
			while (true) {
				id = UUID.randomUUID().toString();
				if (hashesForId(id).size() < 1) break;
				LOG.warn("Discarding colliding random UUID: {}", id);
			}
			storeId(fileData.getHash(), id);
		}

		return id;
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private void makeSchema () throws SQLException {
		if (!tableExists("files")) {
			executeSql("CREATE TABLE files ("
					+ "file STRING NOT NULL PRIMARY KEY, size INT NOT NULL, modified INT NOT NULL, hash STRING NOT NULL);");
		}
		if (!tableExists("hashes")) {
			executeSql("CREATE TABLE hashes ("
					+ "hash STRING NOT NULL PRIMARY KEY, id STRING NOT NULL);");
			executeSql("CREATE INDEX hashes_idx ON hashes (id);");
		}
	}

	private FileData readFileData (final File file) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"SELECT size, modified, hash FROM files WHERE file=?;");
		try {
			st.setString(1, file.getAbsolutePath());
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return null;
				final FileData fileData = new FileData(
						rs.getLong(1), rs.getLong(2), rs.getString(3));
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

	private void storeFileData (final File file, final FileData fileData) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"INSERT INTO files (file,size,modified,hash) VALUES (?,?,?,?);");
		try {
			st.setString(1, file.getAbsolutePath());
			st.setLong(2, fileData.getSize());
			st.setLong(3, fileData.getModified());
			st.setString(4, fileData.getHash());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No insert occured inserting file '" + file.getAbsolutePath() + "'.");
		}
		finally {
			st.close();
		}
	}

	private void updateFileData (final File file, final FileData fileData) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"UPDATE files SET size=?,modified=?,hash=? WHERE file=?;");
		try {
			st.setLong(1, fileData.getSize());
			st.setLong(2, fileData.getModified());
			st.setString(3, fileData.getHash());
			st.setString(4, file.getAbsolutePath());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured updating file '" + file.getAbsolutePath() + "'.");
		}
		finally {
			st.close();
		}
	}

	private String readId (final String hash) throws SQLException {
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

	private void storeId (final String hash, final String id) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"INSERT INTO hashes (hash,id) VALUES (?,?);");
		try {
			st.setString(1, hash);
			st.setString(2, id);
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured inserting hash '" + hash + "'.");
		}
		finally {
			st.close();
		}
	}

	private Collection<String> hashesForId (final String id) throws SQLException {
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

	private static Connection makeDbConnection (final File dbFile) throws SQLException {
		final SQLiteConfig config = new SQLiteConfig();
		config.setEncoding(Encoding.UTF8);
//		config.setUserVersion(version); // TODO
		return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
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
		finally {
			st.close();
		}
	}

}

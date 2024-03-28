package com.vaguehope.dlnatoad.db;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WritableMediaDb implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(WritableMediaDb.class);

	private final Connection conn;
	private final AtomicLong writeCounter;

	protected WritableMediaDb(final Connection conn, final AtomicLong writeCounter) throws SQLException {
		this.writeCounter = writeCounter;
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
			this.writeCounter.incrementAndGet();
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
	// File ID.
	// The read methods are here so they are reading from the same transaction as the writes around them.

	protected FileData readFileData (final File file) throws SQLException {
		return MediaDb.readFileDataFromConn(this.conn, file);
	}

	protected Collection<File> filesWithId(final String id) throws SQLException {
		try (final PreparedStatement st = this.conn.prepareStatement("SELECT file FROM files WHERE id=?;")) {
			st.setString(1, id);
			try (final ResultSet rs = st.executeQuery()) {
				final Collection<File> ret = new ArrayList<>();
				while (rs.next()) {
					ret.add(new File(rs.getString(1)));
				}
				return ret;
			}
		}
	}

	protected Collection<FileAndId> filesWithHash(final String hash) throws SQLException {
		try (final PreparedStatement st = this.conn.prepareStatement("SELECT file,id FROM files WHERE hash=?;")) {
			st.setString(1, hash);
			try (final ResultSet rs = st.executeQuery()) {
				final Collection<FileAndId> ret = new ArrayList<>();
				while (rs.next()) {
					ret.add(new FileAndId(new File(rs.getString(1)), rs.getString(2)));
				}
				return ret;
			}
		}
	}

	public String canonicalIdForHash (final String hash) throws SQLException {
		return MediaDb.canonicalIdForHashFromConn(this.conn, hash);
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
				"INSERT INTO files (file,size,modified,hash,md5,id) VALUES (?,?,?,?,?,?);");
		try {
			st.setString(1, file.getAbsolutePath());
			st.setLong(2, fileData.getSize());
			st.setLong(3, fileData.getModified());
			st.setString(4, fileData.getHash());
			st.setString(5, fileData.getMd5());
			st.setString(6, fileData.getId());
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
				"UPDATE files SET size=?,modified=?,hash=?,md5=?,id=?,missing=? WHERE file=?;");
		try {
			st.setLong(1, fileData.getSize());
			st.setLong(2, fileData.getModified());
			st.setString(3, fileData.getHash());
			st.setString(4, fileData.getMd5());
			st.setString(5, fileData.getId());
			st.setBoolean(6, fileData.isMissing());
			st.setString(7, file.getAbsolutePath());
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

	protected void updateFileAuth(final File file, final BigInteger auth) throws SQLException {
		final PreparedStatement st = this.conn.prepareStatement(
				"UPDATE files SET auth=? WHERE file=?;");
		try {
			st.setString(1, auth.toString(16));
			st.setString(2, file.getAbsolutePath());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured updating auth '" + file.getAbsolutePath() + "'.");
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to update auth for file %s to \"%s\".", file, auth.toString(16)), e);
		}
		finally {
			st.close();
		}
	}

	protected void setFileMissing(final String file, final boolean missing) throws SQLException {
		setFileMissing(file, missing, true);
	}

	protected void setFileMissing(final String file, final boolean missing, final boolean dbMustChange) throws SQLException {
		try (final PreparedStatement st = this.conn.prepareStatement("UPDATE files SET missing=? WHERE file=?;")) {
			st.setBoolean(1, missing);
			st.setString(2, file);
			final int n = st.executeUpdate();
			if (dbMustChange && n < 1) throw new SQLException(String.format("No update occured setting missing=%s for file \"%s\".", missing, file));
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to set missing=%s for file \"%s\".", missing, file), e);
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
			throw new SQLException(String.format("Failed to store canonical ID '%s' for hash '%s'.", id, hash), e);
		}
		finally {
			st.close();
		}
	}

	public Collection<String> hashesForMd5(final String md5) throws SQLException {
		try (final PreparedStatement st = this.conn.prepareStatement("SELECT hash FROM files WHERE md5=?;")) {
			st.setString(1, md5);
			try (final ResultSet rs = st.executeQuery()) {
				final Collection<String> ret = new ArrayList<>();
				while (rs.next()) {
					ret.add(rs.getString(1));
				}
				return ret;
			}
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
	//	Infos.

	protected void storeInfos(final List<FileIdAndInfo> toStore) throws SQLException {
		final List<FileIdAndInfo> toInsert = new ArrayList<>();

		final PreparedStatement stUpdate = this.conn.prepareStatement(
				"UPDATE infos SET size=?,duration=?,width=?,height=? WHERE file_id=?;");
		try {
			for (final FileIdAndInfo fai : toStore) {
				stUpdate.setLong(1, fai.getFile().length());
				stUpdate.setLong(2, fai.getInfo().getDurationMillis());
				stUpdate.setInt(3, fai.getInfo().getWidth());
				stUpdate.setInt(4, fai.getInfo().getHeight());
				stUpdate.setString(5, fai.getFileId());
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

		final Set<String> insertedIds = new HashSet<>();
		final PreparedStatement stInsert = this.conn.prepareStatement(
				"INSERT INTO infos (file_id,size,duration,width,height) VALUES (?,?,?,?,?);");
		try {
			for (final FileIdAndInfo fai : toInsert) {
				if (insertedIds.contains(fai.getFileId())) {
					LOG.info("Skipping writing info duplicate file_id into infos table in same batch: {}", fai.getFileId());
					continue;
				}
				insertedIds.add(fai.getFileId());

				stInsert.setString(1, fai.getFileId());
				stInsert.setLong(2, fai.getFile().length());
				stInsert.setLong(3, fai.getInfo().getDurationMillis());
				stInsert.setInt(4, fai.getInfo().getWidth());
				stInsert.setInt(5, fai.getInfo().getHeight());
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

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
	// Tags.

	/**
	 * Returns true if a change was made, otherwise false.
	 * If tag was previously deleted, will re-add it.
	 * Works like mergeTag() except will not update modified if tag and deleted have not changed.
	 */
	public boolean addTag(final String fileId, final String tag, final long modifiled) throws SQLException {
		return addTag(fileId, tag, "", modifiled);
	}

	public boolean addTag(final String fileId, final String tag, final String cls, final long modifiled) throws SQLException {
		return mergeTag(fileId, tag, cls, modifiled, /* deleted= */false, /* updateModified= */false, /* insertOnly= */false);
	}

	public boolean addTagIfNotDeleted(final String fileId, final String tag, final String cls, final long modifiled) throws SQLException {
		return mergeTag(fileId, tag, cls, modifiled, /* deleted= */false, /* updateModified= */false, /* insertOnly= */true);
	}

	public boolean mergeTag(final String fileId, final String tag, final String cls, final long modifiled, final boolean deleted) throws SQLException {
		return mergeTag(fileId, tag, cls, modifiled, /* deleted= */deleted, /* updateModified= */true, /* insertOnly= */false);
	}

	private boolean mergeTag(final String fileId, final String tag, final String cls, final long modifiled, final boolean deleted, final boolean updateModified, final boolean insertOnly) throws SQLException {
		final Collection<Tag> existing = MediaDb.getTagFromConn(this.conn, fileId, tag, cls);
		if (existing.size() > 1) throw new IllegalStateException(String.format("DB UNIQUE(file_id, tag) constraint failed: id=%s tag='%s'", fileId, tag));
		if (existing.size() > 0) {
			if (insertOnly) return false;

			final Tag e = existing.iterator().next();
			if (e.getModified() >= modifiled) return false;
			if (e.isDeleted() == deleted && !updateModified) return false;

			setTagModifiedAndDeleted(fileId, tag, cls, deleted, modifiled);
			return true;
		}

		try (final PreparedStatement st = this.conn.prepareStatement(
				"INSERT INTO tags (file_id,tag,cls,modified,deleted) VALUES (?,?,?,?,?)")) {
			st.setString(1, fileId);
			st.setString(2, tag);
			st.setString(3, cls);
			st.setLong(4, modifiled);
			st.setBoolean(5, deleted);  // Yes first write might be recoding a deletion.
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException(String.format("No update occured inserting tag for id=%s: tag='%s' cls='%s'", fileId, tag, cls));
			return true;
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to store tag for id=%s: tag='%s' cls='%s'", fileId, tag, cls), e);
		}
	}

	public void setTagModifiedAndDeleted(final String fileId, final String tag, final String cls, final boolean deleted, final long modifiled) throws SQLException {
		try (final PreparedStatement st = this.conn.prepareStatement("UPDATE tags SET deleted=?,modified=? WHERE file_id=? AND tag=? AND cls=?")) {
			st.setInt(1, deleted ? 1 : 0);
			st.setLong(2, modifiled);
			st.setString(3, fileId);
			st.setString(4, tag);
			st.setString(5, cls);
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException(String.format("No update occured setting tag deleted=%s: id=%s tag='%s' cls='%s'", deleted, fileId, tag, cls));
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to set tag deleted=%s: id=%s tag='%s' cls='%s'", deleted, fileId, tag, cls), e);
		}
	}

}
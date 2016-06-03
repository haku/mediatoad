package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.util.HashHelper;

public class MediaId {

	private interface Ider {
		String idForFile (final ContentGroup type, final File file) throws IOException;
	}

	private final Ider impl;

	public MediaId (final File dbFile) throws SQLException {
		this.impl = dbFile != null ? new PersistentIder(new MediaDb(dbFile)) : new TransientIder();
	}

	public String contentId (final ContentGroup type, final File file) throws IOException {
		return this.impl.idForFile(type, file);
	}

	private static class PersistentIder implements Ider {

		private final MediaDb mediaDb;

		public PersistentIder (final MediaDb mediaDb) {
			this.mediaDb = mediaDb;
		}

		@Override
		public String idForFile (final ContentGroup type, final File file) throws IOException {
			try {
				if (file.isFile()) return this.mediaDb.idForFile(file);
				return transientContentId(type, file);
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
		}

	}

	private static class TransientIder implements Ider {

		public TransientIder () {}

		@Override
		public String idForFile (final ContentGroup type, final File file) {
			return transientContentId(type, file);
		}

	}

	private static String transientContentId (final ContentGroup type, final File file) {
		return type.getItemIdPrefix() + (HashHelper.sha1(file.getAbsolutePath()) + "-" + getSafeName(file));
	}

	private static String getSafeName (final File file) {
		return file.getName().replaceAll("[^a-zA-Z0-9]", "_");
	}
}

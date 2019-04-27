package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.util.HashHelper;

public class MediaId {

	private interface Ider {
		void idForFile (final ContentGroup type, final File file, final MediaIdCallback callback) throws IOException;
	}

	private final Ider impl;

	public MediaId (final MediaDb mediaDb) {
		this.impl = mediaDb != null ? new PersistentIder(mediaDb) : new TransientIder();
	}

	public String contentIdSync (final ContentGroup type, final File file) throws IOException {
		StoringMediaIdCallback cb = new StoringMediaIdCallback();
		this.impl.idForFile(type, file, cb);
		return cb.getMediaId();
	}

	public void contentIdAsync (final ContentGroup type, final File file, final MediaIdCallback callback) throws IOException {
		MediaId.this.impl.idForFile(type, file, callback);
	}

	private static class PersistentIder implements Ider {

		private final MediaDb mediaDb;

		public PersistentIder (final MediaDb mediaDb) {
			this.mediaDb = mediaDb;
		}

		@Override
		public void idForFile (final ContentGroup type, final File file, final MediaIdCallback callback) throws IOException {
			try {
				if (file.isFile()) {
					this.mediaDb.idForFile(file, callback);
				}
				else {
					callback.onMediaId(transientContentId(type, file));
				}
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
		}

	}

	private static class TransientIder implements Ider {

		public TransientIder () {}

		@Override
		public void idForFile (final ContentGroup type, final File file, final MediaIdCallback callback) throws IOException {
			callback.onMediaId(transientContentId(type, file));
		}

	}

	private static String transientContentId (final ContentGroup type, final File file) {
		final String hash = HashHelper.sha1(file.getAbsolutePath()) + "-" + getSafeName(file);
		if (type == null) return hash;
		return type.getItemIdPrefix() + hash;
	}

	private static String getSafeName (final File file) {
		return file.getName().replaceAll("[^a-zA-Z0-9]", "_");
	}
}

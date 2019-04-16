package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ScheduledExecutorService;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.util.HashHelper;

public class MediaId {

	private interface Ider {
		String idForFile (final ContentGroup type, final File file) throws IOException;
	}

	private final Ider impl;
	private final ScheduledExecutorService exSvc;

	public MediaId (final MediaDb mediaDb, final ScheduledExecutorService exSvc) {
		this.impl = mediaDb != null ? new PersistentIder(mediaDb) : new TransientIder();
		this.exSvc = exSvc;
	}

	public String contentIdSync (final ContentGroup type, final File file) throws IOException {
		return this.impl.idForFile(type, file);
	}

	public void contentIdAsync (final ContentGroup type, final File file, final MediaIdCallback callback) throws IOException {
		/*
		 * TODO Currently this does not actually work, as slow requests will block quick requests.
		 * The async-ness needs to be pushed down into MediaDb.idForFile() so that it returns
		 * quickly for known files and does hashing on a background thread.
		 */

		// TODO async check futures for failures.
		this.exSvc.submit(new Runnable() {
			@Override
			public void run() {
				try {
					final String id = MediaId.this.impl.idForFile(type, file);
					callback.onMediaId(id);
				} catch (final Exception e) {
					callback.onError(e);
				}
			}
		});
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
		final String hash = HashHelper.sha1(file.getAbsolutePath()) + "-" + getSafeName(file);
		if (type == null) return hash;
		return type.getItemIdPrefix() + hash;
	}

	private static String getSafeName (final File file) {
		return file.getName().replaceAll("[^a-zA-Z0-9]", "_");
	}
}

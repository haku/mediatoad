package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

import com.vaguehope.dlnatoad.db.MediaMetadataStore;
import com.vaguehope.dlnatoad.util.HashHelper;

public class MediaId {

	private interface Ider {
		void idForFile (final ContentGroup type, final File file, final MediaIdCallback callback, final ExecutorService exSvc) throws IOException;
	}

	private final Ider impl;
	private final ExecutorService defExSvc;

	public MediaId (final MediaMetadataStore mediaMetadataStore, final ExecutorService exSvc) {
		this.impl = mediaMetadataStore != null ? new PersistentIder(mediaMetadataStore) : new TransientIder();
		this.defExSvc = exSvc;
	}

	public String contentIdForDirectory (final ContentGroup type, final File file) throws IOException {
		if (!file.isDirectory()) throw new IllegalArgumentException("Not a directory: " + file.getAbsolutePath());
		return transientContentId(type, file);
	}

	@Deprecated
	public String contentIdSync (final ContentGroup type, final File file, final ExecutorService altExSvc) throws IOException {
		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		this.impl.idForFile(type, file, cb, altExSvc);
		return cb.getMediaId();
	}

	public void contentIdAsync (final ContentGroup type, final File file, final MediaIdCallback callback) throws IOException {
		this.impl.idForFile(type, file, callback, this.defExSvc);
	}

	private static class PersistentIder implements Ider {

		private final MediaMetadataStore mediaMetadataStore;

		public PersistentIder (final MediaMetadataStore mediaMetadataStore) {
			this.mediaMetadataStore = mediaMetadataStore;
		}

		@Override
		public void idForFile (final ContentGroup type, final File file, final MediaIdCallback callback, final ExecutorService exSvc) throws IOException {
			try {
				if (file.isFile()) {
					this.mediaMetadataStore.idForFile(file, callback, exSvc);
				}
				else {
					callback.onResult(transientContentId(type, file));
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
		public void idForFile (final ContentGroup type, final File file, final MediaIdCallback callback, final ExecutorService exSvc) throws IOException {
			callback.onResult(transientContentId(type, file));
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

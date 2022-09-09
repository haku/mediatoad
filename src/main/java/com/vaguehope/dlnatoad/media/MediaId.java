package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

import com.vaguehope.dlnatoad.db.MediaMetadataStore;
import com.vaguehope.dlnatoad.util.HashHelper;

public class MediaId {

	private interface Ider {
		void idForFile (final ContentGroup type, final File file, final BigInteger auth, final MediaIdCallback callback) throws IOException;
	}

	private final Ider impl;

	public MediaId (final MediaMetadataStore mediaMetadataStore) {
		this.impl = mediaMetadataStore != null ? new PersistentIder(mediaMetadataStore) : new TransientIder();
	}

	public String contentIdForDirectory (final ContentGroup type, final File file) throws IOException {
		if (!file.isDirectory()) throw new IllegalArgumentException("Not a directory: " + file.getAbsolutePath());
		return transientContentId(type, file);
	}

	public void contentIdAsync (final ContentGroup type, final File file, final BigInteger auth, final MediaIdCallback callback) throws IOException {
		this.impl.idForFile(type, file, auth, callback);
	}

	private static class PersistentIder implements Ider {

		private final MediaMetadataStore mediaMetadataStore;

		public PersistentIder (final MediaMetadataStore mediaMetadataStore) {
			this.mediaMetadataStore = mediaMetadataStore;
		}

		@Override
		public void idForFile (final ContentGroup type, final File file, final BigInteger auth, final MediaIdCallback callback) throws IOException {
			try {
				if (file.isFile()) {
					if (auth == null) throw new NullPointerException("ID of a file requires non-null auth.");
					this.mediaMetadataStore.idForFile(file, auth, callback);
				}
				else {
					callback.onResult(transientContentId(type, file));
				}
			}
			catch (final InterruptedException e) {
				throw new IOException(e);
			}
		}

	}

	private static class TransientIder implements Ider {

		public TransientIder () {}

		@Override
		public void idForFile (final ContentGroup type, final File file, final BigInteger auth, final MediaIdCallback callback) throws IOException {
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

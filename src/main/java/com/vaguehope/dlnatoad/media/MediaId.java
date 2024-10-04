package com.vaguehope.dlnatoad.media;

import java.io.IOException;
import java.math.BigInteger;

import com.vaguehope.dlnatoad.db.MediaMetadataStore;
import com.vaguehope.dlnatoad.fs.MediaFile;
import com.vaguehope.dlnatoad.util.HashHelper;

public class MediaId {

	private interface Ider {
		void idForFile (final ContentGroup type, final MediaFile file, final BigInteger auth, final MediaIdCallback callback) throws IOException;
		void fileGoneAsync(final MediaFile file);
		void putCallbackInQueue(Runnable callback);
	}

	private final Ider impl;

	public MediaId (final MediaMetadataStore mediaMetadataStore) {
		this.impl = mediaMetadataStore != null ? new PersistentIder(mediaMetadataStore) : new TransientIder();
	}

	// TODO rename to contentIdForNode
	public String contentIdForDirectory (final ContentGroup type, final MediaFile file) throws IOException {
		if (!file.isDirectory()) throw new IllegalArgumentException("Not a directory: " + file.getAbsolutePath());
		return transientContentId(type, file);
	}

	public void contentIdAsync (final ContentGroup type, final MediaFile file, final BigInteger auth, final MediaIdCallback callback) throws IOException {
		this.impl.idForFile(type, file, auth, callback);
	}

	public void fileGoneAsync(final MediaFile file) {
		this.impl.fileGoneAsync(file);
	}

	public void putCallbackInQueue(final Runnable callback) {
		this.impl.putCallbackInQueue(callback);
	}

	private static class PersistentIder implements Ider {

		private final MediaMetadataStore mediaMetadataStore;

		public PersistentIder (final MediaMetadataStore mediaMetadataStore) {
			this.mediaMetadataStore = mediaMetadataStore;
		}

		@Override
		public void idForFile (final ContentGroup type, final MediaFile file, final BigInteger auth, final MediaIdCallback callback) throws IOException {
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

		@Override
		public void fileGoneAsync(final MediaFile file) {
			this.mediaMetadataStore.fileGone(file);
		}

		@Override
		public void putCallbackInQueue(final Runnable callback) {
			this.mediaMetadataStore.putCallbackInQueue(callback);
		}

	}

	private static class TransientIder implements Ider {

		public TransientIder () {}

		@Override
		public void idForFile (final ContentGroup type, final MediaFile file, final BigInteger auth, final MediaIdCallback callback) throws IOException {
			callback.onResult(transientContentId(type, file));
		}

		@Override
		public void fileGoneAsync(final MediaFile file) {
			// Nothing.
		}

		@Override
		public void putCallbackInQueue(final Runnable callback) {
			callback.run(); // No queue, so run now.
		}

	}

	private static String transientContentId (final ContentGroup type, final MediaFile file) {
		final String hash = HashHelper.sha1(file.getAbsolutePath()) + "-" + getSafeName(file);
		if (type == null) return hash;
		return type.getItemIdPrefix() + hash;
	}

	private static String getSafeName (final MediaFile file) {
		return file.getName().replaceAll("[^a-zA-Z0-9]", "_");
	}

}

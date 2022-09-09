package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.math.BigInteger;

import com.vaguehope.dlnatoad.media.MediaIdCallback;

public class FileAndIdCallback {

	private final File file;
	private final BigInteger auth;
	private final MediaIdCallback callback;

	public FileAndIdCallback(final File file, final BigInteger auth, final MediaIdCallback callback) {
		if (file == null) throw new IllegalArgumentException("file can not be null.");
		if (auth == null) throw new IllegalArgumentException("auth can not be null, use 0 instead.");
		if (callback == null) throw new IllegalArgumentException("callback can not be null.");
		this.file = file;
		this.auth = auth;
		this.callback = callback;
	}

	public File getFile() {
		return this.file;
	}

	public BigInteger getAuth() {
		return this.auth;
	}

	public MediaIdCallback getCallback() {
		return this.callback;
	}

}

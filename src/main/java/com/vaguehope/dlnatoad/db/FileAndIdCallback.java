package com.vaguehope.dlnatoad.db;

import java.io.File;

import com.vaguehope.dlnatoad.media.MediaIdCallback;

public class FileAndIdCallback {

	private final File file;
	private final MediaIdCallback callback;

	public FileAndIdCallback(final File file, final MediaIdCallback callback) {
		if (file == null) throw new IllegalArgumentException("file can not be null.");
		if (callback == null) throw new IllegalArgumentException("callback can not be null.");
		this.file = file;
		this.callback = callback;
	}

	public File getFile() {
		return this.file;
	}

	public MediaIdCallback getCallback() {
		return this.callback;
	}

}

package com.vaguehope.dlnatoad.db;

import com.vaguehope.dlnatoad.fs.MediaFile;

public class FileAndId {

	private final MediaFile file;
	private final String id;

	public FileAndId(final MediaFile file, final String id) {
		if (file == null) throw new IllegalArgumentException("file can not be null.");
		if (id == null) throw new IllegalArgumentException("id can not be null.");
		this.file = file;
		this.id = id;
	}

	public MediaFile getFile() {
		return this.file;
	}

	public String getId() {
		return this.id;
	}

}

package com.vaguehope.dlnatoad.db;

import java.io.File;

public class FileAndId {

	private final File file;
	private final String id;

	public FileAndId(final File file, final String id) {
		if (file == null) throw new IllegalArgumentException("file can not be null.");
		if (id == null) throw new IllegalArgumentException("id can not be null.");
		this.file = file;
		this.id = id;
	}

	public File getFile() {
		return this.file;
	}

	public String getId() {
		return this.id;
	}

}

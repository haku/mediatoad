package com.vaguehope.dlnatoad.db;

import java.io.File;

public class FileAndData {

	private final File file;
	private final FileData data;

	public FileAndData (final File file, final FileData data) {
		if (file == null) throw new IllegalArgumentException("File can not be null.");
		if (data == null) throw new IllegalArgumentException("Data can not be null.");
		this.file = file;
		this.data = data;
	}

	public File getFile () {
		return this.file;
	}

	public FileData getData () {
		return this.data;
	}

}

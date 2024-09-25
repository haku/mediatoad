package com.vaguehope.dlnatoad.db;

import com.vaguehope.dlnatoad.media.MediaFile;

class FileIdAndInfo {

	private final String fileId;
	private final MediaFile file;
	private final FileInfo info;

	public FileIdAndInfo(final String fileId, final MediaFile file, final FileInfo info) {
		if (fileId == null) throw new IllegalArgumentException("ID can not be null.");
		if (file == null) throw new IllegalArgumentException("File can not be null.");
		if (info == null) throw new IllegalArgumentException("Info can not be null.");
		this.fileId = fileId;
		this.file = file;
		this.info = info;
	}

	public String getFileId() {
		return this.fileId;
	}

	public MediaFile getFile() {
		return this.file;
	}

	public FileInfo getInfo() {
		return this.info;
	}

}

package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;

import com.vaguehope.dlnatoad.util.HashHelper;

public class FileData {

	private final long size;
	private final long modified;
	private final String hash;
	private final String id;

	public FileData (final long size, final long modified, final String hash) {
		if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
		if (hash == null || hash.length() < 1) throw new IllegalArgumentException("Invalid hash: " + hash);
		this.size = size;
		this.modified = modified;
		this.hash = hash;
		this.id = null;
	}

	public FileData (final long size, final long modified, final String hash, final String id) {
		if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
		if (hash == null || hash.length() < 1) throw new IllegalArgumentException("Invalid hash: " + hash);
		if (id == null || id.length() < 1) throw new IllegalArgumentException("Invalid id: " + id);
		this.size = size;
		this.modified = modified;
		this.hash = hash;
		this.id = id;
	}

	public long getSize () {
		return this.size;
	}

	public long getModified () {
		return this.modified;
	}

	public String getHash () {
		return this.hash;
	}

	public String getId () {
		if (this.id == null) throw new IllegalStateException("ID not set.");
		return this.id;
	}

	public boolean upToDate (final File file) {
		return file.length() == this.size && file.lastModified() == this.modified;
	}

	public FileData withId (final String id) {
		if (this.id != null) throw new IllegalStateException("ID already set.");
		return new FileData(this.size, this.modified, this.hash, id);
	}

	public static FileData forFile (final File file) throws IOException {
		return new FileData(file.length(), file.lastModified(), HashHelper.sha1(file).toString(16));
	}

}

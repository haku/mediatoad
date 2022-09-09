package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

import com.google.common.base.Objects;
import com.vaguehope.dlnatoad.util.HashHelper;

public class FileData {

	private final long size;
	private final long modified;
	private final String hash;
	private final String id;
	private final BigInteger auth;

	public FileData (final long size, final long modified, final String hash) {
		if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
		if (hash == null || hash.length() < 1) throw new IllegalArgumentException("Invalid hash: " + hash);
		this.size = size;
		this.modified = modified;
		this.hash = hash;
		this.id = null;
		this.auth = null;
	}

	public FileData (final long size, final long modified, final String hash, final String id, final BigInteger auth) {
		if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
		if (hash == null || hash.length() < 1) throw new IllegalArgumentException("Invalid hash: " + hash);
		if (id == null || id.length() < 1) throw new IllegalArgumentException("Invalid id: " + id);
		this.size = size;
		this.modified = modified;
		this.hash = hash;
		this.id = id;
		this.auth = auth;
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

	public boolean hasAuth(final BigInteger authToCompareTo) {
		return Objects.equal(this.auth, authToCompareTo);
	}

	public boolean upToDate (final File file) {
		return file.length() == this.size && file.lastModified() == this.modified;
	}

	public FileData withId (final String newId) {
		if (this.id != null) throw new IllegalStateException("ID already set.");
		return new FileData(this.size, this.modified, this.hash, newId, this.auth);
	}

	public FileData withNewId (final String newId) {
		if (this.id == null) throw new IllegalStateException("ID not already set.");
		return new FileData(this.size, this.modified, this.hash, newId, this.auth);
	}

	public static FileData forFile (final File file) throws IOException {
		return new FileData(file.length(), file.lastModified(), HashHelper.sha1(file).toString(16));
	}

	@Override
	public String toString() {
		return String.format("FileData{%s, %s, %s, %s}", this.size, this.modified, this.hash, this.id);
	}

}

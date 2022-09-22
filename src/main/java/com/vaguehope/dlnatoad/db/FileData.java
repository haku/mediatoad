package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

import com.google.common.base.Objects;
import com.vaguehope.dlnatoad.util.HashHelper;
import com.vaguehope.dlnatoad.util.HashHelper.Md5AndSha1;

public class FileData {

	private final long size;
	private final long modified;
	private final String hash;
	private final String md5;
	private final String id;
	private final BigInteger auth;
	private final boolean missing;

	public FileData (final long size, final long modified, final String hash, final String md5) {
		if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
		if (hash == null || hash.length() < 1) throw new IllegalArgumentException("Invalid hash: " + hash);
		this.size = size;
		this.modified = modified;
		this.hash = hash;
		this.md5 = md5;
		this.id = null;
		this.auth = null;
		this.missing = false;
	}

	public FileData (final long size, final long modified, final String hash, final String md5, final String id, final BigInteger auth, final boolean missing) {
		if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
		if (hash == null || hash.length() < 1) throw new IllegalArgumentException("Invalid hash: " + hash);
		if (id == null || id.length() < 1) throw new IllegalArgumentException("Invalid id: " + id);
		this.size = size;
		this.modified = modified;
		this.hash = hash;
		this.md5 = md5;
		this.id = id;
		this.auth = auth;
		this.missing = missing;
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

	public String getMd5() {
		return this.md5;
	}

	public String getId () {
		if (this.id == null) throw new IllegalStateException("ID not set.");
		return this.id;
	}

	public boolean hasAuth(final BigInteger authToCompareTo) {
		return Objects.equal(this.auth, authToCompareTo);
	}

	public boolean isMissing() {
		return this.missing;
	}

	public boolean upToDate (final File file) {
		return file.length() == this.size && file.lastModified() == this.modified;
	}

	public FileData withId (final String newId) {
		if (this.id != null) throw new IllegalStateException("ID already set.");
		return new FileData(this.size, this.modified, this.hash, this.md5, newId, this.auth, this.missing);
	}

	public FileData withMd5 (final String newMd5) {
		if (this.md5 != null) throw new IllegalStateException("MD5 already set.");
		return new FileData(this.size, this.modified, this.hash, newMd5, this.id, this.auth, this.missing);
	}

	public FileData withNewId (final String newId) {
		if (this.id == null) throw new IllegalStateException("ID not already set.");
		return new FileData(this.size, this.modified, this.hash, this.md5, newId, this.auth, this.missing);
	}

	public static FileData forFile (final File file) throws IOException {
		final Md5AndSha1 hashes = HashHelper.generateMd5AndSha1(file);
		return new FileData(file.length(), file.lastModified(), hashes.getSha1().toString(16), hashes.getMd5().toString(16));
	}

	@Override
	public String toString() {
		return String.format("FileData{%s, %s, %s, %s}", this.size, this.modified, this.hash, this.id);
	}

}

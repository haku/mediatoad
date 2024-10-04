package com.vaguehope.dlnatoad.db;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;

import com.vaguehope.dlnatoad.fs.MediaFile;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.util.HashHelper.Md5AndSha1;

public class FileData {

	private final long size;
	private final long modified;
	private final String hash;
	private final String md5;
	private final String mimeType;
	private final String id;
	private final BigInteger auth;
	private final boolean missing;

	public FileData (final long size, final long modified, final String hash, final String md5, final String mimeType) {
		if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
		if (hash == null || hash.length() < 1) throw new IllegalArgumentException("Invalid hash: " + hash);
		if (mimeType != null && mimeType.length() == 0) throw new IllegalArgumentException("Mime type can not be empty string.");
		this.size = size;
		this.modified = modified;
		this.hash = hash;
		this.md5 = md5;
		this.mimeType = mimeType;
		this.id = null;
		this.auth = null;
		this.missing = false;
	}

	public FileData (final long size, final long modified, final String hash, final String md5, final String mimeType, final String id, final BigInteger auth, final boolean missing) {
		if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
		if (hash == null || hash.length() < 1) throw new IllegalArgumentException("Invalid hash: " + hash);
		if (mimeType != null && mimeType.length() == 0) throw new IllegalArgumentException("Mime type can not be empty string.");
		if (id == null || id.length() < 1) throw new IllegalArgumentException("Invalid id: " + id);
		this.size = size;
		this.modified = modified;
		this.hash = hash;
		this.md5 = md5;
		this.mimeType = mimeType;
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
		return Objects.equals(this.auth, authToCompareTo);
	}

	public boolean isMissing() {
		return this.missing;
	}

	public String getMimeType() {
		return this.mimeType;
	}

	public boolean upToDate (final MediaFile file) {
		return file.length() == this.size && file.lastModified() == this.modified;
	}

	public FileData withId (final String newId) {
		if (this.id != null) throw new IllegalStateException("ID already set.");
		return new FileData(this.size, this.modified, this.hash, this.md5, this.mimeType, newId, this.auth, this.missing);
	}

	public FileData withMd5 (final String newMd5) {
		if (this.md5 != null) throw new IllegalStateException("MD5 already set.");
		return new FileData(this.size, this.modified, this.hash, newMd5, this.mimeType, this.id, this.auth, this.missing);
	}

	public FileData withMimeType (final String newMimeType) {
		if (this.mimeType != null) throw new IllegalStateException("Mime type already set.");
		return new FileData(this.size, this.modified, this.hash, this.md5, newMimeType, this.id, this.auth, this.missing);
	}

	public FileData withNewId (final String newId) {
		if (this.id == null) throw new IllegalStateException("ID not already set.");
		return new FileData(this.size, this.modified, this.hash, this.md5, this.mimeType, newId, this.auth, this.missing);
	}

	public static FileData forFile (final MediaFile file) throws IOException {
		final Md5AndSha1 hashes = file.generateMd5AndSha1();
		final MediaFormat format = MediaFormat.identify(file);
		return new FileData(
				file.length(),
				file.lastModified(),
				hashes.getSha1().toString(16),
				hashes.getMd5().toString(16),
				format != null ? format.getMime() : null);
	}

	@Override
	public String toString() {
		return String.format("FileData{%s, %s, %s, %s, %s, %s, %s, %s}",
				this.size, this.modified, this.hash, this.md5, this.mimeType, this.id, this.auth, this.missing);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.size, this.modified, this.hash, this.md5, this.mimeType, this.id, this.auth, this.missing);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof FileData)) return false;
		final FileData that = (FileData) obj;
		return Objects.equals(this.size, that.size)
				&& Objects.equals(this.modified, that.modified)
				&& Objects.equals(this.hash, that.hash)
				&& Objects.equals(this.md5, that.md5)
				&& Objects.equals(this.mimeType, that.mimeType)
				&& Objects.equals(this.id, that.id)
				&& Objects.equals(this.auth, that.auth)
				&& Objects.equals(this.missing, that.missing);
	}

}

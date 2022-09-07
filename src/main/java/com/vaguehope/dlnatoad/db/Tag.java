package com.vaguehope.dlnatoad.db;

import java.util.Objects;

public class Tag {

	private final String tag;
	private final long modified;
	private final boolean deleted;

	public Tag(final String tag, final long modified, final boolean deleted) {
		if (tag == null || tag.isEmpty()) throw new IllegalArgumentException("tag can not be null or empty.");
		this.tag = tag;
		this.modified = modified;
		this.deleted = deleted;
	}

	public String getTag() {
		return this.tag;
	}

	public long getModified() {
		return this.modified;
	}

	public boolean isDeleted() {
		return this.deleted;
	}

	@Override
	public String toString() {
		return String.format("Tag{%s, %s, %s}", this.tag, this.modified, this.deleted);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.tag, this.modified, this.deleted);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof Tag)) return false;
		final Tag that = (Tag) obj;
		return Objects.equals(this.tag, that.tag)
				&& Objects.equals(this.modified, that.modified)
				&& Objects.equals(this.deleted, that.deleted);
	}

}

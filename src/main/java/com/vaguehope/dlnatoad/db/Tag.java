package com.vaguehope.dlnatoad.db;

public class Tag {

	private final long id;
	private final String tag;
	private final long modified;
	private final boolean deleted;

	public Tag(final long id, final String tag, final long modified, final boolean deleted) {
		if (id < 0) throw new IllegalArgumentException("id can not be negative.");
		if (tag == null || tag.isEmpty()) throw new IllegalArgumentException("tag can not be null or empty.");
		this.id = id;
		this.tag = tag;
		this.modified = modified;
		this.deleted = deleted;
	}

	public long getId() {
		return this.id;
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
		return String.format("Tag{%s, %s, %s, %s}", this.id, this.tag, this.modified, this.deleted);
	}

	// no equals() ATM as i can not decide if matching all fields is useful.

}

package com.vaguehope.dlnatoad.dlnaserver;

public abstract class AbstractContent {

	protected final String id;
	protected final String parentId;
	protected final String title;

	public AbstractContent(final String id, final String parentId, final String title) {
		if (id == null) throw new IllegalArgumentException("id must not be null.");
		this.id = id;
		this.parentId = parentId;
		this.title = title;
	}

	public String getId() {
		return this.id;
	}

	public String getParentId() {
		return this.parentId;
	}

	public String getTitle() {
		return this.title;
	}

}

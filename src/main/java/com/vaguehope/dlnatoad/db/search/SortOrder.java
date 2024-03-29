package com.vaguehope.dlnatoad.db.search;

public class SortOrder {

	public static SortOrder FILE = new SortOrder("file COLLATE NOCASE");
	public static SortOrder MODIFIED = new SortOrder("modified");

	private final String column;
	private final String direction;

	protected SortOrder(String column) {
		this.column = column;
		this.direction = null;
	}

	protected SortOrder(String column, String direction) {
		this.column = column;
		this.direction = direction;
	}

	public SortOrder asc() {
		return new SortOrder(this.column, "ASC");
	}

	public SortOrder desc() {
		return new SortOrder(this.column, "DESC");
	}

	public String toSql() {
		if (this.direction == null) throw new IllegalStateException("direciton not set.");
		return this.column + " " + this.direction;
	}

}

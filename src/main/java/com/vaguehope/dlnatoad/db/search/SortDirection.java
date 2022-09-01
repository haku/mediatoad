package com.vaguehope.dlnatoad.db.search;

public enum SortDirection {
	ASC(" ASC"),
	DESC(" DESC");

	private final String sql;

	SortDirection(final String sql) {
		this.sql = sql;
	}

	/**
	 * Includes leading space.
	 */
	public String getSql() {
		return this.sql;
	}

}

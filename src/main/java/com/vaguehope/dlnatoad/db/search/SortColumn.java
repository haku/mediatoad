package com.vaguehope.dlnatoad.db.search;

public enum SortColumn {

	FILE("file COLLATE NOCASE"),
	FILE_SIZE("size"),
	MODIFIED("modified"),
	DURATION("duration");

	private final String sqlColumnName;

	private SortColumn(final String column) {
		this.sqlColumnName = column;
	}

	public SortOrder asc() {
		return new SortOrder(this, "ASC");
	}

	public SortOrder desc() {
		return new SortOrder(this, "DESC");
	}

	public static class SortOrder {
		private final SortColumn column;
		private final String direction;

		private SortOrder(final SortColumn column, final String direction) {
			this.column = column;
			this.direction = direction;
		}

		public SortColumn column() {
			return this.column;
		}

		public String toSql() {
			if (this.direction == null) throw new IllegalStateException("direciton not set.");
			return this.column.sqlColumnName + " " + this.direction;
		}
	}

}

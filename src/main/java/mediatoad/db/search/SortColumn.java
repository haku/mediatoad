package mediatoad.db.search;

public enum SortColumn {

	FILE_PATH("files.file COLLATE NOCASE"),
	FILE_SIZE("files.size"),
	MODIFIED("files.modified"),
	DURATION("infos.duration"),
	LAST_PLAYED("playback.last_played"),
	START_COUNT("playback.start_count"),
	COMPLETE_COUNT("playback.complete_count");

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

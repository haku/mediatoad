package com.vaguehope.dlnatoad.db;

public class Sqlite {

	/**
	 * This pairs with escapeSearch().
	 */
	public static final String SEARCH_ESC = "\\";

	/**
	 * This pairs with SEARCH_ESC.
	 */
	public static String escapeSearch(final String term) {
		String q = term;
		q = q.replace("\\", "\\\\");
		q = q.replace("%", "\\%");
		q = q.replace("_", "\\_");
		q = q.replace("*", "%");
		return q;
	}

}

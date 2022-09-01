package com.vaguehope.dlnatoad.db.search;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.Sqlite;

public class DbSearchParser {

	private static final int MAX_SEARCH_TERMS = 10;

	private static final String _SQL_MEDIAFILES_SELECT =
			"SELECT id FROM files";

	private static final String _SQL_WHERE = " WHERE";
	private static final String _SQL_AND = " AND";
	private static final String _SQL_OR = " OR";

	private static final String _SQL_MEDIAFILES_WHERES_FILE =
			" (file LIKE ? ESCAPE ?)";

	private static final String _SQL_MEDIAFILES_WHERES_NOT_FILE =
			" NOT " + _SQL_MEDIAFILES_WHERES_FILE;

	private static final String _SQL_MEDIAFILES_WHERES_TAG =
			" (id IN (SELECT file_id FROM tags WHERE tag LIKE ? ESCAPE ? AND deleted=0))";

	private static final String _SQL_MEDIAFILES_WHERES_NOT_TAG =
			" NOT " + _SQL_MEDIAFILES_WHERES_TAG;

	private static final String _SQL_MEDIAFILES_WHERES_FILEORTAG =
			" (file LIKE ? ESCAPE ? OR id IN (SELECT file_id FROM tags WHERE tag LIKE ? ESCAPE ? AND deleted=0))";

	private static final String _SQL_MEDIAFILES_SEARCHORDERBY =
			" ORDER BY file COLLATE NOCASE ASC";

	private DbSearchParser () {
		throw new AssertionError();
	}

	public static DbSearch parseSearch (final String allTerms) {
		return parseSearch(null, null, allTerms);
	}

	public static DbSearch parseSearch (final String allTerms,
			final String[] sortColumns, final SortDirection[] sortDirection) {
		return parseSearch(sortColumns, sortDirection, allTerms);
	}

	public static DbSearch parseSearch (
			final String[] sortColumns, final SortDirection[] sortDirection) {
		return parseSearch(sortColumns, sortDirection, null);
	}

	public static DbSearch parseSearch (
			final String[] sortColumns, final SortDirection[] sortDirection,
			final String allTerms) {
		if (sortColumns == null ^ sortDirection == null) throw new IllegalArgumentException("Must specify both or neith of sort and direction.");
		if (sortColumns != null && sortDirection != null && sortColumns.length != sortDirection.length) throw new IllegalArgumentException("Sorts and directions must be same length.");

		final StringBuilder sql = new StringBuilder(_SQL_MEDIAFILES_SELECT);
		final List<String> terms = QuerySplitter.split(allTerms, MAX_SEARCH_TERMS);
		appendWhere(sql, terms);
		if (sortColumns != null && sortDirection != null && sortColumns.length > 0 && sortDirection.length > 0) {
			sql.append(" ORDER BY ");
			for (int i = 0; i < sortColumns.length; i++) {
				if (i > 0) sql.append(",");
				sql.append(sortColumns[i]).append(sortDirection[i].getSql());
			}
		}
		else {
			sql.append(_SQL_MEDIAFILES_SEARCHORDERBY);
		}
		sql.append(";");
		return new DbSearch(sql.toString(), terms);
	}

	private static void appendWhere (final StringBuilder sql, final List<String> terms) {
		sql.append(_SQL_WHERE);
		boolean needAnd = false;

		if (terms.size() > 0) {
			if (needAnd) sql.append(_SQL_AND);
			needAnd = true;

			sql.append(" ( ");
			int openBrackets = 0;
			for (int i = 0; i < terms.size(); i++) {
				final String term = terms.get(i);
				final String prevTerm = i > 0 ? terms.get(i - 1) : null;
				final String nextTerm = i < terms.size() - 1 ? terms.get(i + 1) : null;

				if ("OR".equals(term)) {
					if (prevTerm == null || nextTerm == null) continue; // Ignore leading and trailing.
					if ("OR".equals(prevTerm)) continue;
					if ("AND".equals(prevTerm)) continue;
					if ("(".equals(prevTerm)) continue;
					if (")".equals(nextTerm)) continue;
					sql.append(_SQL_OR);
					continue;
				}

				if ("AND".equals(term)) {
					if (prevTerm == null || nextTerm == null) continue; // Ignore leading and trailing.
					if ("OR".equals(prevTerm)) continue;
					if ("AND".equals(prevTerm)) continue;
					if ("(".equals(prevTerm)) continue;
					if (")".equals(nextTerm)) continue;
					sql.append(_SQL_AND);
					continue;
				}

				if (")".equals(term)) {
					if (openBrackets > 0) {
						sql.append(" ) ");
						openBrackets -= 1;
					}
					continue;
				}

				if (i > 0) {
					// Not the first term and not following OR or AND.
					if (!"OR".equals(prevTerm) && !"AND".equals(prevTerm) && !"(".equals(prevTerm)) {
						sql.append(_SQL_AND);
					}
				}

				if ("(".equals(term)) {
					sql.append(" ( ");
					openBrackets += 1;
				}
				else if (isFileMatchPartial(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_FILE);
				}
				else if (isFileNotMatchPartial(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_NOT_FILE);
				}
				else if (isTagMatchPartial(term) || isTagMatchExact(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_TAG);
				}
				else if (isTagNotMatchPartial(term) || isTagNotMatchExact(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_NOT_TAG);
				}
				else {
					sql.append(_SQL_MEDIAFILES_WHERES_FILEORTAG);
				}
			}

			// Tidy any unclosed brackets.
			for (int i = 0; i < openBrackets; i++) {
				sql.append(" ) ");
			}

			sql.append(" ) ");
		}
	}

	protected static boolean isFileMatchPartial (final String term) {
		return term.startsWith("f~") || term.startsWith("F~");
	}

	protected static boolean isFileNotMatchPartial (final String term) {
		return term.startsWith("-f~") || term.startsWith("-F~");
	}

	protected static boolean isTagMatchPartial (final String term) {
		return term.startsWith("t~") || term.startsWith("T~");
	}

	protected static boolean isTagNotMatchPartial (final String term) {
		return term.startsWith("-t~") || term.startsWith("-T~");
	}

	protected static boolean isTagMatchExact (final String term) {
		return term.startsWith("t=") || term.startsWith("T=");
	}

	protected static boolean isTagNotMatchExact (final String term) {
		return term.startsWith("-t=") || term.startsWith("-T=");
	}

	protected static String removeMatchOperator (final String term) {
		int x = term.indexOf('=');
		if (x < 0) x = term.indexOf('~');
		if (x < 0) throw new IllegalArgumentException("term does not contain '=' or '~': " + term);
		return term.substring(x + 1);
	}

	protected static String anchoredOrWildcardEnds (final String term) {
		String ret = term;
		if (ret.startsWith("^")) {
			ret = ret.substring(1);
		}
		else {
			ret = "%" + ret;
		}
		if (ret.endsWith("$")) {
			ret = ret.substring(0, ret.length() - 1);
		}
		else {
			ret = ret + "%";
		}
		return ret;
	}

	public static class DbSearch {

		private final String sql;
		private final List<String> terms;

		public DbSearch (final String sql, final List<String> terms) {
			this.sql = sql;
			this.terms = terms;
		}

		String getSql() {
			return this.sql;
		}

		List<String> getTerms() {
			return this.terms;
		}

		public List<String> execute (final MediaDb db) throws SQLException {
			return execute(db, -1);
		}

		public List<String> execute (final MediaDb db, final int maxResults) throws SQLException {
			try (final PreparedStatement ps = db.prepare(this.sql)) {
				int parmIn = 1;
				for (final String term : this.terms) {
					if ("OR".equals(term)) continue;
					if ("AND".equals(term)) continue;
					if ("(".equals(term)) continue;
					if (")".equals(term)) continue;
					if (isFileMatchPartial(term) || isFileNotMatchPartial(term)
							|| isTagMatchPartial(term) || isTagNotMatchPartial(term)) {
						ps.setString(parmIn++, anchoredOrWildcardEnds(Sqlite.escapeSearch(QuoteRemover.unquote(removeMatchOperator(term)))));
						ps.setString(parmIn++, Sqlite.SEARCH_ESC);
					}
					else if (isTagMatchExact(term) || isTagNotMatchExact(term)) {
						ps.setString(parmIn++, Sqlite.escapeSearch(QuoteRemover.unquote(removeMatchOperator(term))));
						ps.setString(parmIn++, Sqlite.SEARCH_ESC);
					}
					else {
						final String escapedTerm = Sqlite.escapeSearch(QuoteRemover.unquote(term));
						ps.setString(parmIn++, "%" + escapedTerm + "%");
						ps.setString(parmIn++, Sqlite.SEARCH_ESC);
						ps.setString(parmIn++, "%" + escapedTerm + "%");
						ps.setString(parmIn++, Sqlite.SEARCH_ESC);
					}
				}
				if (maxResults > 0) ps.setMaxRows(maxResults);
				try (final ResultSet rs = ps.executeQuery()) {
					return parseRecordSet(rs);
				}
			}
		}

		private static List<String> parseRecordSet(final ResultSet rs) throws SQLException {
			final List<String> ret = new ArrayList<>();
			if (rs.next()) {
				do {
					ret.add(rs.getString(1));
				}
				while (rs.next());
			}
			return ret;
		}

		@Override
		public String toString () {
			return new StringBuilder("Search{")
					.append("sql=" + this.sql)
					.append(", terms=" + this.terms)
					.append("}")
					.toString();
		}

	}

}

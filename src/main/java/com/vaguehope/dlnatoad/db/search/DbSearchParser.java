package com.vaguehope.dlnatoad.db.search;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.SqlFragments;
import com.vaguehope.dlnatoad.db.Sqlite;

public class DbSearchParser {

	private static final int MAX_SEARCH_TERMS = 10;

	private static final String _SQL_MEDIAFILES_SELECT =
			"SELECT id FROM files WHERE missing=0 AND";

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

	public static DbSearch parseSearch (final String allTerms, final Set<BigInteger> authIds) {
		return parseSearch(allTerms, authIds, null, null);
	}

	public static DbSearch parseSearch (
			final String allTerms,
			final Set<BigInteger> authIds,
			final String[] sortColumns,
			final SortDirection[] sortDirection) {
		if (sortColumns == null ^ sortDirection == null) throw new IllegalArgumentException("Must specify both or neith of sort and direction.");
		if (sortColumns != null && sortDirection != null && sortColumns.length != sortDirection.length) throw new IllegalArgumentException("Sorts and directions must be same length.");

		final StringBuilder sql = new StringBuilder(_SQL_MEDIAFILES_SELECT);
		SqlFragments.appendWhereAuth(sql, authIds);
		final List<String> terms = QuerySplitter.split(allTerms, MAX_SEARCH_TERMS);
		appendWhereTerms(sql, terms);
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
		return new DbSearch(sql.toString(), terms);
	}

	private static void appendWhereTerms (final StringBuilder sql, final List<String> terms) {
		if (terms.size() > 0) {
			sql.append(_SQL_AND);
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
				else if (DbSearchSyntax.isFileMatchPartial(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_FILE);
				}
				else if (DbSearchSyntax.isFileNotMatchPartial(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_NOT_FILE);
				}
				else if (DbSearchSyntax.isTagMatchPartial(term) || DbSearchSyntax.isTagMatchExact(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_TAG);
				}
				else if (DbSearchSyntax.isTagNotMatchPartial(term) || DbSearchSyntax.isTagNotMatchExact(term)) {
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
			return execute(db, -1, 0);
		}

		public List<String> execute (final MediaDb db, final int maxResults, final int offset) throws SQLException {
			try (final PreparedStatement ps = db.prepare(maybeAddLimit(this.sql, maxResults, offset))) {
				int parmIn = 1;
				for (final String term : this.terms) {
					if ("OR".equals(term)) continue;
					if ("AND".equals(term)) continue;
					if ("(".equals(term)) continue;
					if (")".equals(term)) continue;
					if (DbSearchSyntax.isFileMatchPartial(term) || DbSearchSyntax.isFileNotMatchPartial(term)
							|| DbSearchSyntax.isTagMatchPartial(term) || DbSearchSyntax.isTagNotMatchPartial(term)) {
						ps.setString(parmIn++, anchoredOrWildcardEnds(Sqlite.escapeSearch(QuoteRemover.unquote(DbSearchSyntax.removeMatchOperator(term)))));
						ps.setString(parmIn++, Sqlite.SEARCH_ESC);
					}
					else if (DbSearchSyntax.isTagMatchExact(term) || DbSearchSyntax.isTagNotMatchExact(term)) {
						ps.setString(parmIn++, Sqlite.escapeSearch(QuoteRemover.unquote(DbSearchSyntax.removeMatchOperator(term))));
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

		private static String maybeAddLimit(final String sql, final int maxResults, final int offset) {
			if (maxResults < 0) return sql;
			return String.format("%s LIMIT %d OFFSET %d", sql, maxResults, offset);
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

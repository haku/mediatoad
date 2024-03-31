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
import com.vaguehope.dlnatoad.db.TagFrequency;

public class DbSearchParser {

	private static final int MAX_SEARCH_TERMS = 10;

	// join against hashes so that only canonical IDs are returned,
	// as non canonical IDs will be dropped by ContentTree.getItemsForIds() anyway.
	private static final String _SQL_MEDIAFILES_SELECT =
			"SELECT id FROM files INNER JOIN hashes USING (id) WHERE missing=0";

	private static final String _SQL_TAG_FREQUENCY_SELECT =
			"SELECT DISTINCT tag, COUNT(DISTINCT file_id) AS freq"
			+ " FROM files, tags"
			+ " WHERE id=file_id AND missing=0 AND deleted=0 AND cls NOT LIKE '.%' AND id IN (FILEQUERY)"
			+ " GROUP BY tag"
			+ " ORDER BY freq DESC, tag ASC;";

	private static final String _SQL_AND = " AND";
	private static final String _SQL_OR = " OR";

	private static final String _SQL_MEDIAFILES_WHERES_FILE =
			" (file LIKE ? ESCAPE ?)";

	private static final String _SQL_MEDIAFILES_WHERES_NOT_FILE =
			" NOT " + _SQL_MEDIAFILES_WHERES_FILE;

	private static final String _SQL_MEDIAFILES_WHERES_TYPE =
			" (mimetype LIKE ? ESCAPE ?)";

	private static final String _SQL_MEDIAFILES_WHERES_TAG =
			" (id IN (SELECT file_id FROM tags WHERE tag LIKE ? ESCAPE ? AND deleted=0))";

	private static final String _SQL_MEDIAFILES_WHERES_NOT_TAG =
			" NOT " + _SQL_MEDIAFILES_WHERES_TAG;

	private static final String _SQL_MEDIAFILES_WHERES_TAG_COUNT_LESS_THAN =
			" (id NOT IN (SELECT file_id FROM tags WHERE deleted=0 GROUP BY file_id HAVING COUNT(DISTINCT tag) >= ?))";

	private static final String _SQL_MEDIAFILES_WHERES_TAG_COUNT_GREATER_THAN =
			" (id IN (SELECT file_id FROM tags WHERE deleted=0 GROUP BY file_id HAVING COUNT(DISTINCT tag) > ?))";

	private static final String _SQL_MEDIAFILES_WHERE_INFOS =
			" (id IN (SELECT file_id FROM infos WHERE WOH?))";

	private static final String _SQL_MEDIAFILES_WHERES_FILEORTAG =
			" (file LIKE ? ESCAPE ? OR id IN (SELECT file_id FROM tags WHERE tag LIKE ? ESCAPE ? AND deleted=0))";

	private DbSearchParser () {
		throw new AssertionError();
	}

	public static DbSearch parseSearch (
			final String allTerms,
			final Set<BigInteger> authIds,
			final SortOrder sort) {
		return parseSearch(allTerms, authIds, false, new SortOrder[] { sort });
	}

	public static DbSearch parseSearchWithAuthBypass (
			final String allTerms,
			final SortOrder sort) {
		return parseSearch(allTerms, null, true, new SortOrder[] { sort });
	}

	private static DbSearch parseSearch (
			final String allTerms,
			final Set<BigInteger> authIds,
			final boolean bypassAuthChecks,
			final SortOrder[] sort) {
		if (sort == null || sort.length < 1) throw new IllegalArgumentException("Sort must be specified");

		final StringBuilder sql = new StringBuilder(_SQL_MEDIAFILES_SELECT);
		if (!bypassAuthChecks) {
			sql.append(_SQL_AND);
			SqlFragments.appendWhereAuth(sql, authIds);
		}

		final List<String> terms = QuerySplitter.split(allTerms, MAX_SEARCH_TERMS);
		appendWhereTerms(sql, terms);

		sql.append(" ORDER BY ");
		for (int i = 0; i < sort.length; i++) {
			if (i > 0) sql.append(",");
			sql.append(sort[i].toSql());
		}

		return new DbSearch(sql.toString(), terms);
	}

	public static TagFrequencySearch parseSearchForTags (
			final String allTerms,
			final Set<BigInteger> authIds) {

		final StringBuilder fileQuery = new StringBuilder(_SQL_MEDIAFILES_SELECT);
		fileQuery.append(_SQL_AND);
		SqlFragments.appendWhereAuth(fileQuery, authIds);

		final List<String> terms = QuerySplitter.split(allTerms, MAX_SEARCH_TERMS);
		appendWhereTerms(fileQuery, terms);

		final String tagQuery = _SQL_TAG_FREQUENCY_SELECT.replace("FILEQUERY", fileQuery.toString());
		return new TagFrequencySearch(tagQuery, terms);
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

				String woh;

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
				else if (DbSearchSyntax.isTypeMatchExactOrPartial(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_TYPE);
				}
				else if (DbSearchSyntax.isTagMatchPartial(term) || DbSearchSyntax.isTagMatchExact(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_TAG);
				}
				else if (DbSearchSyntax.isTagNotMatchPartial(term) || DbSearchSyntax.isTagNotMatchExact(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_NOT_TAG);
				}
				else if (DbSearchSyntax.isTagCountLessThan(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_TAG_COUNT_LESS_THAN);
				}
				else if (DbSearchSyntax.isTagCountGreaterThan(term)) {
					sql.append(_SQL_MEDIAFILES_WHERES_TAG_COUNT_GREATER_THAN);
				}
				else if ((woh = DbSearchSyntax.widthOrHeight(term)) != null) {
					sql.append(_SQL_MEDIAFILES_WHERE_INFOS.replace("WOH", woh));
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

	public static class DbSearch extends Search<String> {
		public DbSearch (final String sql, final List<String> terms) {
			super(sql, terms);
		}

		@Override
		protected String parseRecord(final ResultSet rs) throws SQLException {
			return rs.getString(1);
		}
	}

	public static class TagFrequencySearch extends Search<TagFrequency> {
		public TagFrequencySearch (final String sql, final List<String> terms) {
			super(sql, terms);
		}

		@Override
		protected TagFrequency parseRecord(final ResultSet rs) throws SQLException {
			return new TagFrequency(rs.getString(1), rs.getInt(2));
		}
	}

	private abstract static class Search<T> {

		private final String sql;
		private final List<String> terms;

		public Search (final String sql, final List<String> terms) {
			this.sql = sql;
			this.terms = terms;
		}

		String getSql() {
			return this.sql;
		}

		List<String> getTerms() {
			return this.terms;
		}

		public List<T> execute (final MediaDb db) throws SQLException {
			return execute(db, -1, 0);
		}

		public List<T> execute (final MediaDb db, final int maxResults, final int offset) throws SQLException {
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
					else if (DbSearchSyntax.isTypeMatchExactOrPartial(term)) {
						String escapedTerm = Sqlite.escapeSearch(QuoteRemover.unquote(DbSearchSyntax.removeMatchOperator(term)));
						if (DbSearchSyntax.isTypeMatchPartial(term)) escapedTerm += "%";
						ps.setString(parmIn++, escapedTerm);
						ps.setString(parmIn++, Sqlite.SEARCH_ESC);
					}
					else if (DbSearchSyntax.isTagMatchExact(term) || DbSearchSyntax.isTagNotMatchExact(term)) {
						ps.setString(parmIn++, Sqlite.escapeSearch(QuoteRemover.unquote(DbSearchSyntax.removeMatchOperator(term))));
						ps.setString(parmIn++, Sqlite.SEARCH_ESC);
					}
					else if (DbSearchSyntax.isTagCountLessThan(term) || DbSearchSyntax.isTagCountGreaterThan(term)
							|| DbSearchSyntax.widthOrHeight(term) != null) {
						ps.setInt(parmIn++, DbSearchSyntax.removeCountOperator(term));
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

		private List<T> parseRecordSet(final ResultSet rs) throws SQLException {
			final List<T> ret = new ArrayList<>();
			while (rs.next()) {
				ret.add(parseRecord(rs));
			}
			return ret;
		}

		protected abstract T parseRecord(final ResultSet rs) throws SQLException;

		@Override
		public String toString () {
			return new StringBuilder(getClass().getSimpleName() + "{")
					.append("sql=(" + this.sql)
					.append("), terms=" + this.terms)
					.append("}")
					.toString();
		}

	}

}

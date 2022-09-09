package com.vaguehope.dlnatoad.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sqlite {

	/**
	 * This pairs with escapeSearch().
	 */
	public static final String SEARCH_ESC = "\\";

	private static final Logger LOG = LoggerFactory.getLogger(Sqlite.class);

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

	public static void addColumnIfMissing(final Connection conn, final String tableName, final String colName, final String colType) throws SQLException {
		try (final PreparedStatement p = conn.prepareStatement("SELECT name FROM pragma_table_info(?) WHERE name=?;")) {
			p.setString(1, tableName);
			p.setString(2, colName);
			try (final ResultSet rs = p.executeQuery()) {
				if (rs.next()) return;
			}
		}
		final String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s;", tableName, colName, colType);
		try (final Statement s = conn.createStatement()) {
			s.execute(sql);
		}
		LOG.info("Added column {} to {}.", colName, tableName);
	}

}

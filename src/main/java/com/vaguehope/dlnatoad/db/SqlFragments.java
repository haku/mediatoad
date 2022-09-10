package com.vaguehope.dlnatoad.db;

import java.math.BigInteger;
import java.util.Set;

public class SqlFragments {

	public static void appendWhereAuth(final StringBuilder sql, final Set<BigInteger> authIds) {
		sql.append(" auth IN ('0'");
		if (authIds != null) {
			for (final BigInteger authId : authIds) {
				sql.append(",'");
				sql.append(authId.toString(16));
				sql.append("'");

			}
		}
		sql.append(")");
	}

}

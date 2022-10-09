package com.vaguehope.dlnatoad.db.search;

import org.apache.commons.lang3.StringUtils;

public class DbSearchSyntax {

	public static String makeSingleTagSearch(String tag) {
		final String quote;
		if (StringUtils.containsAny(tag, ' ', '(', ')', '\t', '　')) {
			if (tag.indexOf('"') >= 0) {
				if (tag.indexOf('\'') >= 0) {
					tag = tag.replace("'", "\\'");
				}
				quote = "'";
			}
			else {
				quote = "\"";
			}
		}
		else {
			quote = "";
		}
		final StringBuilder ret = new StringBuilder();
		ret.append("t=");
		ret.append(quote);
		ret.append(tag);
		ret.append(quote);
		return ret.toString();
	}

	public static boolean isFileMatchPartial (final String term) {
		return term.startsWith("f~") || term.startsWith("F~");
	}

	public static boolean isFileNotMatchPartial (final String term) {
		return term.startsWith("-f~") || term.startsWith("-F~");
	}

	public static boolean isTagMatchPartial (final String term) {
		return term.startsWith("t~") || term.startsWith("T~");
	}

	public static boolean isTagNotMatchPartial (final String term) {
		return term.startsWith("-t~") || term.startsWith("-T~");
	}

	public static boolean isTagMatchExact (final String term) {
		return term.startsWith("t=") || term.startsWith("T=");
	}

	public static boolean isTagNotMatchExact (final String term) {
		return term.startsWith("-t=") || term.startsWith("-T=");
	}

	public static String removeMatchOperator (final String term) {
		int x = term.indexOf('=');
		if (x < 0) x = term.indexOf('~');
		if (x < 0) throw new IllegalArgumentException("term does not contain '=' or '~': " + term);
		return term.substring(x + 1);
	}


}

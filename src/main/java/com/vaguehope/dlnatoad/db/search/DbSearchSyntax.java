package com.vaguehope.dlnatoad.db.search;

import java.io.File;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableSet;

public class DbSearchSyntax {

	public static String makeSingleTagSearch(String tag) {
		return "t=" + quoteSearchTerm(tag);
	}

	public static String makePathSearch(final File file) {
		return "f~^" + quoteSearchTerm(file.getAbsolutePath());
	}

	private static String quoteSearchTerm(final String term) {
		String t = term;
		final String quote;
		if (StringUtils.containsAny(t, ' ', '(', ')', '\t', '　')) {
			if (t.indexOf('"') >= 0) {
				if (t.indexOf('\'') >= 0) {
					t = t.replace("'", "\\'");
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
		return quote + t + quote;
	}

	public static boolean isFileMatchPartial (final String term) {
		return term.startsWith("f~") || term.startsWith("F~");
	}

	public static boolean isFileNotMatchPartial (final String term) {
		return term.startsWith("-f~") || term.startsWith("-F~");
	}

	public static boolean isTypeMatchExactOrPartial (final String term) {
		return term.toLowerCase(Locale.ENGLISH).startsWith("type=");
	}

	/**
	 * type=image/jpeg is an exact match.
	 * type=image is an implicit prefix search.
	 */
	public static boolean isTypeMatchPartial (final String term) {
		return isTypeMatchExactOrPartial(term) && term.indexOf('/') < 0;
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

	public static boolean isTagCountLessThan (final String term) {
		return term.startsWith("t<") || term.startsWith("T<");
	}

	public static boolean isTagCountGreaterThan (final String term) {
		return term.startsWith("t>") || term.startsWith("T>");
	}

	private static final Set<String> NUMBER_OPERATORS = ImmutableSet.of("=", "<", ">", "<=", ">=");

	// Valid:
	// wWhH = < <= > >=
	public static String widthOrHeight (final String term) {
		final String field;
		if (term.startsWith("w") || term.startsWith("W")) {
			field = "width";
		}
		else if (term.startsWith("h") || term.startsWith("H")) {
			field = "height";
		}
		else {
			return null;
		}

		final String op1 = term.length() >= 2 ? term.substring(1, 2) : null;
		final String op2 = term.length() >= 3 ? term.substring(2, 3) : null;

		if (NUMBER_OPERATORS.contains(op1 + op2)) {
			return field + op1 + op2;
		}
		else if (NUMBER_OPERATORS.contains(op1)) {
			return field + op1;
		}

		return null;
	}

	/**
	 * If input is invalid default value is 1.
	 */
	public static int removeCountOperator(final String term) {
		int x = term.indexOf('<');
		if (x < 0) x = term.indexOf('>');
		if (x < 0) x = term.indexOf('=');
		if (x < 0) throw new IllegalArgumentException("term does not contain '<' or '>' or '=': " + term);

		// support <= and >=
		if (x < term.length() - 1 && term.charAt(x + 1) == '=') x += 1;

		final String s = term.substring(x + 1);
		if (s.length() < 1) return 1;

		try {
			return Integer.parseInt(s);
		}
		catch (final NumberFormatException e) {
			return 1;
		}
	}

}

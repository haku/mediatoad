package com.vaguehope.dlnatoad.util;

import java.util.Collection;
import java.util.regex.Pattern;

public final class StringHelper {

	private StringHelper () {
		throw new AssertionError();
	}

	public static String unquoteQuotes (final String in) {
		final String s = in.replace("\\\"", "\"");
		if (s.startsWith("\"")) {
			if (s.endsWith("\"")) return s.substring(1, s.length() - 1);
			return s.substring(1, s.length());
		}
		if (s.endsWith("\"")) return s.substring(0, s.length() - 1);
		return s;
	}

	public static String join (final Collection<?> arr, final String sep) {
		final StringBuilder s = new StringBuilder();
		for (final Object obj : arr) {
			if (s.length() > 0) s.append(sep);
			s.append(obj.toString());
		}
		return s.toString();
	}

	public static String join (final String before, final String after, final Collection<?> arr, final String sep) {
		final StringBuilder s = new StringBuilder();
		s.append(before);
		boolean first = true;
		for (final Object obj : arr) {
			if (!first) s.append(sep);
			first = false;
			s.append(obj.toString());
		}
		s.append(after);
		return s.toString();
	}

	/**
	 * Returns 2 element array or null.
	 */
	public static String[] splitOnce (final String s, final char sep) {
		final int x = s.indexOf(sep);
		if (x < 0) return null;
		return new String[] { s.substring(0, x), s.substring(x + 1) };
	}

	private static final Pattern END_QUOTES = Pattern.compile("^['\"]+|['\"]+$");

	public static String removeEndQuotes (final String s) {
		return END_QUOTES.matcher(s).replaceAll("");
	}

	public static String removePrefix(String s, String prefix) {
		if (s == null) return null;
		if (s.startsWith(prefix)) {
			return s.substring(prefix.length());
		}
		return s;
	}

}

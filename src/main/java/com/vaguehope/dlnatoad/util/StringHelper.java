package com.vaguehope.dlnatoad.util;

import java.util.Collection;

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

}

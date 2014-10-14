package com.vaguehope.dlnatoad.util;

public final class StringHelper {

	private StringHelper () {
		throw new AssertionError();
	}


	public static String unquoteQuotes(final String in) {
		final String s = in.replace("\\\"", "\"");
		if (s.startsWith("\"")) {
			if (s.endsWith("\"")) return s.substring(1, s.length() - 1);
			return s.substring(1, s.length());
		}
		if (s.endsWith("\"")) return s.substring(0, s.length() - 1);
		return s;
	}

}

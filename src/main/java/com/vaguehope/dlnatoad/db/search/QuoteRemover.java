package com.vaguehope.dlnatoad.db.search;

public class QuoteRemover {

	public static String unquote(String input) {
		if (input == null) return null;
		final StringBuilder ret = new StringBuilder();
		boolean escaped = false;
		char quote = 0;
		for (int i = 0; i < input.length(); i++) {
			final char c = input.charAt(i);

			if (escaped) {
				ret.append(c);
				escaped = false;
				continue;
			}

			boolean appendC = false;
			switch (c) {
			case '\\':
				escaped = true;
				// Drop the \ if the escaped char is the quote type we are inside.
				if (safeCharAt(input, i + 1, 'a') != quote) {
					appendC = true;
				}
				break;
			case '"':
			case '\'':
				if (quote == c) {
					quote = 0;
				}
				else if (quote == 0 && findUnescaped(input, i + 1, c) > i) {
					quote = c;
				}
				else {
					appendC = true;
				}
				break;
			default:
				appendC = true;
			}

			if (appendC) ret.append(c);
		}
		return ret.toString();
	}

	private static int safeCharAt(final String input, final int i, final char def) {
		if (i >= input.length()) return def;
		return input.charAt(i);
	}

	private static int findUnescaped(String input, int start, char toFind) {
		boolean escaped = false;
		for (int i = start; i < input.length(); i++) {
			if (escaped) {
				escaped = false;
				continue;
			}
			final char c = input.charAt(i);
			if (c == '\\') {
				escaped = true;
			}
			else if (c == toFind) {
				return i;
			}
		}
		return -1;
	}

}

package com.vaguehope.dlnatoad.db.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuerySplitter {

	public static List<String> split(final String input, final int maxParts) {
		if (input == null) return Collections.emptyList();
		final List<String> ret = new ArrayList<>();
		final StringBuilder part = new StringBuilder();
		boolean escaped = false;
		boolean prevWasWhitespace = true;
		char quote = 0;
		for (int i = 0; i < input.length(); i++) {
			final char c = input.charAt(i);

			if (escaped) {
				part.append(c);
				escaped = false;
				continue;
			}

			boolean finishPreviousPart = false;
			boolean appendC = false;
			boolean finishAfterAppend = false;
			boolean isWhiteSpace = false;
			switch (c) {
			case '\\':
				escaped = true;
				appendC = true;
				break;
			case '(':
				appendC = true;
				if (prevWasWhitespace) {
					finishPreviousPart = true;
					finishAfterAppend = true;
				}
				break;
			case ')':
				appendC = true;
				if (Character.isWhitespace(safeCharAt(input, i + 1, ' '))) {
					finishPreviousPart = true;
					finishAfterAppend = true;
				}
				break;
			case '"':
			case '\'':
				appendC = true;
				if (quote == c) {
					quote = 0;
				}
				else if (quote == 0) {
					quote = c;
				}
				break;
			case ' ':
			case '\t':
			case '　':
				isWhiteSpace = true;
				if (quote == 0) {
					finishPreviousPart = true;
				}
				else {
					appendC = true;
				}
				break;
			default:
				appendC = true;
			}

			if (finishPreviousPart) {
				if (part.length() > 0) ret.add(part.toString());
				part.setLength(0);
			}

			if (ret.size() >= maxParts) return ret;

			if (appendC) part.append(c);

			if (finishAfterAppend) {
				if (part.length() > 0) ret.add(part.toString());
				part.setLength(0);
			}

			prevWasWhitespace = isWhiteSpace;
		}
		if (part.length() > 0) ret.add(part.toString());
		return ret;
	}

	private static int safeCharAt(final String input, final int i, final char def) {
		if (i >= input.length()) return def;
		return input.charAt(i);
	}

}

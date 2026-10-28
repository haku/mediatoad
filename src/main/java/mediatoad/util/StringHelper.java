package mediatoad.util;

import java.util.Collection;
import java.util.Enumeration;
import java.util.regex.Matcher;
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

	public static String join (final Enumeration<String> en, final String join) {
		if (en == null || !en.hasMoreElements()) return null;
		final StringBuilder s = new StringBuilder(en.nextElement());
		while (en.hasMoreElements()) {
			s.append(join).append(en.nextElement());
		}
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

	public static String removePrefix(final String s, final String prefix) {
		if (s == null) return null;
		if (s.startsWith(prefix)) {
			return s.substring(prefix.length());
		}
		return s;
	}

	public static String removeFirstMatchingPrefix(final String s, final String... prefixes) {
		if (s == null) return null;
		for (final String prefix : prefixes) {
			if (s.startsWith(prefix)) {
				return s.substring(prefix.length());
			}
		}
		return s;
	}

	public static String removeSuffix(final String s, final String suffix) {
		if (s == null) return null;
		if (s.endsWith(suffix)) {
			return s.substring(0, s.length() - suffix.length());
		}
		return s;
	}

	private static final Pattern LINEBREAKS = Pattern.compile("\\R|\\v");

	/**
	 * Keep just the first line of text with a max length, and append a number to indicate how many char were removed.
	 */
	public static String firstLineMaxLength(final String input, final int maxLen) {
		if (input == null) return null;
		if (input.length() < 1) return input;

		final String trimmed = input.length() > maxLen ? input.substring(0, maxLen) : input;

		final Matcher m = LINEBREAKS.matcher(trimmed);
		final String first = m.find(0) ? trimmed.substring(0, m.start()) : trimmed;

		if (first.length() < input.length()) return first + "[+" + (input.length() - first.length()) + "]";
		return first;
	}

}

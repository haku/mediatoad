package faecommon.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;

public class StringsCommon {

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

	public static String escapeHtml(final Object a) {
		return StringEscapeUtils.escapeHtml4(String.valueOf(a));
	}

}

package com.vaguehope.dlnatoad.util;

public class ExceptionHelper {

	public static String causeTrace(final Throwable t) {
		final StringBuilder sb = new StringBuilder();
		boolean first = true;
		Throwable c = t;
		while (c != null) {
			if (!first) sb.append(" > ");
			sb.append(String.valueOf(c));
			c = c.getCause();
			first = false;
		}
		return sb.toString();
	}

}

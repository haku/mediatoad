package com.vaguehope.dlnatoad.util;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ThreadSafeDateFormatter {

	private final FormatTl threadLocal;

	public ThreadSafeDateFormatter (final String format) {
		this.threadLocal = new FormatTl(format, Locale.getDefault(Locale.Category.FORMAT));
	}

	public ThreadSafeDateFormatter (final String format, Locale locale) {
		this.threadLocal = new FormatTl(format, locale);
	}

	public SimpleDateFormat get () {
		return this.threadLocal.get();
	}

	private final class FormatTl extends ThreadLocal<SimpleDateFormat> {

		private final String format;
		private final Locale locale;

		public FormatTl (final String format, Locale locale) {
			this.format = format;
			this.locale = locale;
		}

		@Override
		protected SimpleDateFormat initialValue () {
			final SimpleDateFormat a = new SimpleDateFormat(this.format, this.locale);
			//a.setTimeZone(TimeZone.getTimeZone("UTC"));
			return a;
		}
	}

}

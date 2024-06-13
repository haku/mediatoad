package com.vaguehope.dlnatoad.util;

import java.text.DecimalFormat;

public final class FileHelper {

	private static final int I_1024 = 1024;

	private FileHelper () {}

	public static String readableFileSize (final long size) {
		// http://stackoverflow.com/questions/3263892/format-file-size-as-mb-gb-etc
		if (size <= 0) return "0";
		final String[] units = new String[] { "B", "KiB", "MiB", "GiB", "TiB" };
		final int digitGroups = (int) (Math.log10(size) / Math.log10(I_1024));
		return new DecimalFormat("#,##0.#").format(size / Math.pow(I_1024, digitGroups)) + " " + units[digitGroups];
	}

}

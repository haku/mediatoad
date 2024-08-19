package com.vaguehope.dlnatoad.util;

import java.io.File;
import java.text.DecimalFormat;

import org.apache.commons.io.FilenameUtils;

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

	/**
	 * relative path from root to sub dir, including the name of the root dir.
	 */
	public static String rootAndPath(final File rootDir, final File subDir) {
		if (rootDir == null || subDir == null) throw new IllegalArgumentException();

		final String root = FilenameUtils.getFullPath(FilenameUtils.normalizeNoEndSeparator(rootDir.getAbsolutePath(), true));
		final String sub = FilenameUtils.normalize(subDir.getAbsolutePath(), true);

		if (!sub.startsWith(root)) throw new IllegalArgumentException("'" + sub + "' does not start with '" + root + "'");

		String rel = sub.substring(root.length());
		if (rel.startsWith("/")) rel = rel.substring(1);

		return rel;
	}

}

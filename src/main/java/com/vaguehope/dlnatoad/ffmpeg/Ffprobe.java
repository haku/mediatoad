package com.vaguehope.dlnatoad.ffmpeg;

import java.io.File;
import java.io.IOException;

public class Ffprobe {

	private static Boolean isAvailable = null;

	public static boolean isAvailable () {
		if (isAvailable == null) {
			try {
				isAvailable = ProcessHelper.runAndWait("ffprobe", "-version").size() > 0;
			}
			catch (final IOException e) {
				isAvailable = false;
			}
		}
		return isAvailable.booleanValue();
	}

	private static void checkAvailable() throws IOException {
		if (!isAvailable()) throw new IOException("ffprobe not avilable.");
	}

	/**
	 * Will not return null.
	 */
	public static FfprobeInfo inspect (final File inFile) throws IOException {
		checkAvailable();
		final FfprobeParser parser = new FfprobeParser();
		ProcessHelper.runAndWait(new String[] {
				"ffprobe",
				"-hide_banner",
				"-show_streams",
				"-show_format",
				"-print_format", "flat",
				inFile.getAbsolutePath()
		}, parser);
		return parser.build();
	}

}

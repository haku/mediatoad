package com.vaguehope.dlnatoad.ffmpeg;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ffprobe {

	private static final String FFPROBE = "ffprobe";
	private static final Logger LOG = LoggerFactory.getLogger(Ffprobe.class);

	private static Boolean isAvailable = null;

	public static boolean isAvailable () {
		if (isAvailable == null) {
			try {
				isAvailable = ProcessHelper.runAndWait(FFPROBE, "-version").size() > 0;
			}
			catch (final IOException e) {
				isAvailable = false;
				LOG.warn("{} not found, file media info such as duration will not be read.", FFPROBE);
			}
		}
		return isAvailable.booleanValue();
	}

	private static void checkAvailable() throws IOException {
		if (!isAvailable()) throw new IOException(FFPROBE + " not avilable.");
	}

	/**
	 * Will not return null.
	 */
	public static FfprobeInfo inspect (final File inFile) throws IOException {
		checkAvailable();
		final FfprobeParser parser = new FfprobeParser();
		ProcessHelper.runAndWait(new String[] {
				FFPROBE,
				"-hide_banner",
				"-show_streams",
				"-show_format",
				"-print_format", "flat",
				inFile.getAbsolutePath()
		}, parser);
		return parser.build();
	}

}

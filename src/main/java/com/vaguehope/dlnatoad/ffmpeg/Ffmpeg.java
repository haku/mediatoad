package com.vaguehope.dlnatoad.ffmpeg;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ffmpeg {

	private static final String FFMPEG = "ffmpeg";
	private static final Logger LOG = LoggerFactory.getLogger(Ffprobe.class);

	private static Boolean isAvailable = null;

	public static boolean isAvailable () {
		if (isAvailable == null) {
			try {
				isAvailable = ProcessHelper.runAndWait(FFMPEG, "-version").size() > 0;
			}
			catch (final IOException e) {
				isAvailable = false;
				LOG.warn("{} not found, video thumbnails can not be generated.", FFMPEG);
			}
		}
		return isAvailable.booleanValue();
	}

	private static void checkAvailable() throws IOException {
		if (!isAvailable()) throw new IOException(FFMPEG + " not avilable.");
	}

	public static void generateThumbnail (final File videoFile, final int size, final File thumbnailFile) throws IOException {
		checkAvailable();
		// https://ffmpeg.org/ffmpeg-filters.html#select_002c-aselect
		// https://ffmpeg.org/ffmpeg-filters.html#scale-1
		// https://ffmpeg.org/ffmpeg-utils.html
		// TODO what if video is <300 frames?
		ProcessHelper.runAndWait(new String[] {
				FFMPEG,
				"-hide_banner",
				"-loglevel", "error",
				"-y",
				"-i", videoFile.getAbsolutePath(),
				"-vf", "select=gt(scene\\,0.5)+gt(n\\,300),"
						+ "scale='if(gt(in_w,in_h)," + size + ",-1)':'if(gt(in_h,in_w)," + size + ",-1)'",
				"-frames:v", "1",
				thumbnailFile.getAbsolutePath()
		});
	}

}

package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FilenameUtils;

public final class MediaIdentifier {

	public static final FileFilter FILE_FILTER = new MediaFileFilter();

	private static final Map<String, String> TYPES;
	static {
		final Map<String, String> t = new ConcurrentHashMap<String, String>();
		t.put("avi", "video/avi");
		t.put("mp4", "video/mp4");
		t.put("m4v", "video/mp4");
		t.put("mkv", "video/x-matroska");
		t.put("flv", "video/x-flv");
		t.put("wmv", "video/x-ms-wmv");
		t.put("mpg", "video/mpeg");
		t.put("mpeg", "video/mpeg");
		TYPES = Collections.unmodifiableMap(t);
	}

	private MediaIdentifier () {
		throw new AssertionError();
	}

	public static String getMimeType (final File file) {
		return TYPES.get(FilenameUtils.getExtension(file.getName()).toLowerCase());
	}

	private static class MediaFileFilter implements FileFilter {

		public MediaFileFilter () {}

		@Override
		public boolean accept (final File file) {
			return getMimeType(file) != null;
		}

	}

}

package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FilenameUtils;

public enum MediaFormat {

	AVI("avi", "video/avi", MediaType.VIDEO),
	MP4("mp4", "video/mp4", MediaType.VIDEO),
	M4V("m4v", "video/mp4", MediaType.VIDEO),
	MKV("mkv", "video/x-matroska", MediaType.VIDEO),
	FLV("flv", "video/x-flv", MediaType.VIDEO),
	WMV("wmv", "video/x-ms-wmv", MediaType.VIDEO),
	MPG("mpg", "video/mpeg", MediaType.VIDEO),
	MPEG("mpeg", "video/mpeg", MediaType.VIDEO),
	JPG("jpg", "image/jpeg", MediaType.IMAGE),
	JPEG("jpeg", "image/jpeg", MediaType.IMAGE),
	PNG("png", "image/png", MediaType.IMAGE);

	public static final FileFilter FILE_FILTER = new MediaFileFilter();

	private static final Map<String, MediaFormat> EXT_TO_FORMAT;
	static {
		final Map<String, MediaFormat> t = new ConcurrentHashMap<String, MediaFormat>(MediaFormat.values().length);
		for (MediaFormat f : MediaFormat.values()) {
			t.put(f.ext, f);
		}
		EXT_TO_FORMAT = Collections.unmodifiableMap(t);
	}

	private final String ext;
	private final String mime;
	private final MediaType type;

	private MediaFormat (final String ext, final String mime, final MediaType type) {
		this.ext = ext;
		this.mime = mime;
		this.type = type;
	}

	public String getMime () {
		return this.mime;
	}

	public MediaType getType () {
		return this.type;
	}

	public static MediaFormat identify (final File file) {
		return EXT_TO_FORMAT.get(FilenameUtils.getExtension(file.getName()).toLowerCase());
	}

	private static class MediaFileFilter implements FileFilter {

		public MediaFileFilter () {}

		@Override
		public boolean accept (final File file) {
			return identify(file) != null;
		}

	}

}

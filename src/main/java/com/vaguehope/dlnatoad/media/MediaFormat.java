package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FilenameUtils;

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;

public enum MediaFormat {

	AVI("avi", "video/avi", ContentGroup.VIDEO),
	MP4("mp4", "video/mp4", ContentGroup.VIDEO),
	M4V("m4v", "video/mp4", ContentGroup.VIDEO),
	MKV("mkv", "video/x-matroska", ContentGroup.VIDEO),
	FLV("flv", "video/x-flv", ContentGroup.VIDEO),
	WMV("wmv", "video/x-ms-wmv", ContentGroup.VIDEO),
	MPG("mpg", "video/mpeg", ContentGroup.VIDEO),
	MPEG("mpeg", "video/mpeg", ContentGroup.VIDEO),
	JPG("jpg", "image/jpeg", ContentGroup.IMAGE),
	JPEG("jpeg", "image/jpeg", ContentGroup.IMAGE),
	PNG("png", "image/png", ContentGroup.IMAGE),
	MP3("mp3", "audio/mpeg", ContentGroup.AUDIO),
	OGG("ogg", "audio/ogg", ContentGroup.AUDIO),
	;

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
	private final ContentGroup contentGroup;

	private MediaFormat (final String ext, final String mime, final ContentGroup type) {
		this.ext = ext;
		this.mime = mime;
		this.contentGroup = type;
	}

	public String getMime () {
		return this.mime;
	}

	public ContentGroup getContentGroup () {
		return this.contentGroup;
	}

	public static MediaFormat identify (final File file) {
		return identify(file.getName());
	}

	public static MediaFormat identify (final String name) {
		return EXT_TO_FORMAT.get(FilenameUtils.getExtension(name).toLowerCase());
	}

	public static enum MediaFileFilter implements FileFilter {
		INSTANCE;

		@Override
		public boolean accept (final File file) {
			return identify(file) != null;
		}

	}

}

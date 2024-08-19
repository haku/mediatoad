package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.jetty.http.MimeTypes;
import org.jupnp.util.MimeType;

public enum MediaFormat {

	AVI("avi", "video/avi", ContentGroup.VIDEO),
	FLV("flv", "video/x-flv", ContentGroup.VIDEO),
	M4V("m4v", "video/mp4", ContentGroup.VIDEO),
	MKV("mkv", "video/x-matroska", ContentGroup.VIDEO),
	MOV("mov", "video/quicktime", ContentGroup.VIDEO),
	MP4("mp4", "video/mp4", ContentGroup.VIDEO),
	MPEG("mpeg", "video/mpeg", ContentGroup.VIDEO),
	MPG("mpg", "video/mpeg", ContentGroup.VIDEO),
	OGM("ogm", "video/ogg", ContentGroup.VIDEO),
	OGV("ogv", "video/ogg", ContentGroup.VIDEO),
	RMVB("rmvb", "application/vnd.rn-realmedia-vbr", ContentGroup.VIDEO),
	WEBM("webm", "video/webm", ContentGroup.VIDEO),
	WMV("wmv", "video/x-ms-wmv", ContentGroup.VIDEO),
	_3GP("3gp", "video/3gpp", ContentGroup.VIDEO),

	GIF("gif", "image/gif", ContentGroup.IMAGE),
	JPEG("jpeg", "image/jpeg", ContentGroup.IMAGE),
	JPG("jpg", "image/jpeg", ContentGroup.IMAGE),
	NEF("nef", "image/x-nikon-nef", ContentGroup.IMAGE),
	PNG("png", "image/png", ContentGroup.IMAGE),
	TIF("tif", "image/tiff", ContentGroup.IMAGE),
	TIFF("tiff", "image/tiff", ContentGroup.IMAGE),
	WEBP("webp", "image/webp", ContentGroup.IMAGE),

	AAC("aac", "audio/aac", ContentGroup.AUDIO),
	AC3("ac3", "audio/ac3", ContentGroup.AUDIO),
	FLAC("flac", "audio/flac", ContentGroup.AUDIO),
	M4A("m4a", "audio/mp4", ContentGroup.AUDIO),
	MP3("mp3", "audio/mpeg", ContentGroup.AUDIO),
	MPGA("mpga", "audio/mpeg", ContentGroup.AUDIO),
	OGA("oga", "audio/ogg", ContentGroup.AUDIO),
	OGG("ogg", "audio/ogg", ContentGroup.AUDIO),
	OPUS("opus", "audio/ogg", ContentGroup.AUDIO),
	RA("ra", "audio/vnd.rn-realaudio", ContentGroup.AUDIO),
	WAV("wav", "audio/wave", ContentGroup.AUDIO),
	WMA("wma", "audio/x-ms-wma", ContentGroup.AUDIO),

	SRT("srt", "text/srt", ContentGroup.SUBTITLES),
	SSA("ssa", "text/x-ssa", ContentGroup.SUBTITLES),
	ASS("ass", "text/x-ass", ContentGroup.SUBTITLES),

	PDF("pdf", "application/pdf", ContentGroup.DOCUMENT),
	;

	private static final Map<String, MediaFormat> EXT_TO_FORMAT;
	static {
		final Map<String, MediaFormat> t = new ConcurrentHashMap<>(MediaFormat.values().length);
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

	public String getExt() {
		return this.ext;
	}

	public String getMime () {
		return this.mime;
	}

	public ContentGroup getContentGroup () {
		return this.contentGroup;
	}

	public MimeType asMimetype() {
		final int slash = this.mime.indexOf('/');
		return new MimeType(this.mime.substring(0, slash), this.mime.substring(slash + 1));
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

	public static void addTo(final MimeTypes mimeTypes) {
		for (final MediaFormat mf : values()) {
			mimeTypes.addMimeMapping(mf.getExt(), mf.getMime());
		}
	}

}

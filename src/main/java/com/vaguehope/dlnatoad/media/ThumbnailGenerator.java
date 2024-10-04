package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import com.vaguehope.dlnatoad.ffmpeg.Ffmpeg;
import com.vaguehope.dlnatoad.fs.MediaFile;
import com.vaguehope.dlnatoad.util.HashHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class ThumbnailGenerator {

	private static final int THUMB_SIZE_PIXELS = 200;
	private static final float THUMB_QUALITY = 0.8f;

	private static final Object[] LOCK = new Object[0];

	private final File cacheDir;
	private final ImageResizer imageResizer;

	public ThumbnailGenerator(final File cacheDir) {
		this.cacheDir = cacheDir;
		this.imageResizer = new ImageResizer();
	}

	public boolean supported(final ContentGroup group, final boolean videoThumbs) {
		switch (group) {
		case IMAGE:
			return true;
		case VIDEO:
			return videoThumbs && Ffmpeg.isAvailable();
		default:
			return false;
		}
	}

	public File generate(final ContentItem item) throws IOException {
		final MediaFile inF = item.getFile();
		if (!inF.exists()) throw new IllegalArgumentException("File does not exist: " + inF.getAbsolutePath());

		final File outF = chooseOutputFile(inF, THUMB_SIZE_PIXELS);
		if (outF.exists() && outF.lastModified() > inF.lastModified()) return outF;

		// TODO do something better than this nasty rate-limiting hack.
		synchronized (LOCK) {
			FileUtils.forceMkdir(outF.getParentFile());
			doGenerate(item, inF, outF);
		}
		return outF;
	}

	private void doGenerate(final ContentItem item, final MediaFile inF, final File outF) throws IOException {
		switch (item.getFormat().getContentGroup()) {
		case IMAGE:
			this.imageResizer.scaleImageToFile(inF, THUMB_SIZE_PIXELS, THUMB_QUALITY, outF);
			return;
		case VIDEO:
			if (!Ffmpeg.isAvailable()) return;
			Ffmpeg.generateThumbnail(inF, THUMB_SIZE_PIXELS, outF);
			return;
		default:
		}
	}

	private File chooseOutputFile(final MediaFile inF, final int size) {
		final String outName = HashHelper.md5(inF.getAbsolutePath()).toString(16) + "_" + size + ".jpg";
		final File outDir = new File(new File(this.cacheDir, outName.substring(0, 1)), outName.substring(1, 2));
		return new File(outDir, outName);
	}

}

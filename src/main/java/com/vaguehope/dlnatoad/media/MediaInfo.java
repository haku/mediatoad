package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.db.FileInfo;
import com.vaguehope.dlnatoad.db.MediaMetadataStore;
import com.vaguehope.dlnatoad.ffmpeg.Ffprobe;
import com.vaguehope.dlnatoad.ffmpeg.FfprobeInfo;
import com.vaguehope.dlnatoad.fs.MediaFile;
import com.vaguehope.dlnatoad.util.ExceptionHelper;

public class MediaInfo {

	private static final Logger LOG = LoggerFactory.getLogger(MediaInfo.class);

	private final MediaMetadataStore mediaMetadataStore;
	private final ThumbnailGenerator thumbnailGenerator;
	private final ExecutorService exSvc;


	public MediaInfo () {
		this(null, null, null);
	}

	public MediaInfo (final MediaMetadataStore mediaMetadataStore, final ThumbnailGenerator thumbnailGenerator, final ExecutorService exSvc) {
		this.mediaMetadataStore = mediaMetadataStore;
		this.thumbnailGenerator = thumbnailGenerator;
		this.exSvc = exSvc;
	}

	public void readInfoAsync (final MediaFile file, final ContentItem item) {
		if (this.mediaMetadataStore != null) {
			this.exSvc.submit(new ReadInfoJob(file, item, this.mediaMetadataStore));
		}

		// TODO read dir prefs for video_thumbs prefs.
		if (this.thumbnailGenerator != null && this.thumbnailGenerator.supported(item.getFormat().getContentGroup(), false)) {
			this.exSvc.submit(() -> generateThumbnail(item));
		}
	}

	private static class ReadInfoJob implements Runnable {

		private final MediaFile file;
		private final ContentItem item;
		private final MediaMetadataStore mediaMetadataStore;

		public ReadInfoJob (final MediaFile file, final ContentItem item, final MediaMetadataStore mediaMetadataStore) {
			this.file = file;
			this.item = item;
			this.mediaMetadataStore = mediaMetadataStore;
		}

		@Override
		public void run () {
			try {
				final FileInfo info = readInfo();
				if (info != null) {
					if (info.hasDuration()) {
						this.item.setDurationMillis(info.getDurationMillis());
					}
					if (info.hasWidthAndHeight()) {
						this.item.setWidthAndHeight(info.getWidth(), info.getHeight());
					}
				}
			}
			catch (final Exception e) {
				LOG.warn("Failed to read info: \"{}\" {}", this.file.getAbsolutePath(), e.toString());
			}
		}

		private FileInfo readInfo () throws IOException, SQLException, InterruptedException {
			final FileInfo storedInfo = this.mediaMetadataStore.readFileInfo(this.item.getId(), this.file);
			if (storedInfo != null) return storedInfo;

			if (!Ffprobe.isAvailable()) return null;
			if (!this.file.exists()) return null;  // did file disappear between being put on the queue and now?

			final FfprobeInfo probeInfo = Ffprobe.inspect(this.file);
			if (probeInfo.hasDuration() || probeInfo.hasWidthAndHeight()) {
				final FileInfo info = new FileInfo(probeInfo.getDurationMillis(), probeInfo.getWidth(), probeInfo.getHeight());
				this.mediaMetadataStore.storeFileInfoAsync(this.item.getId(), this.file, info);
				return info;
			}

			LOG.warn("Failed to read info: {}", this.file.getAbsolutePath());
			return null;
		}

	}

	private void generateThumbnail(final ContentItem item) {
		try {
			this.thumbnailGenerator.generate(item);
		}
		catch (final IOException e) {
			LOG.warn("Failed to generate thumbnail for {}: {}", item.getFile(), ExceptionHelper.causeTrace(e));
		}
	}

}

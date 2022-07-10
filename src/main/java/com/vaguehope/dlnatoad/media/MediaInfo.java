package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.db.MediaMetadataStore;
import com.vaguehope.dlnatoad.ffmpeg.Ffprobe;
import com.vaguehope.dlnatoad.ffmpeg.FfprobeInfo;

public class MediaInfo {

	private static final Logger LOG = LoggerFactory.getLogger(MediaInfo.class);

	private final MediaMetadataStore mediaMetadataStore;
	private final ExecutorService exSvc;

	public MediaInfo () {
		this(null, null);
	}

	public MediaInfo (final MediaMetadataStore mediaMetadataStore, final ExecutorService exSvc) {
		this.mediaMetadataStore = mediaMetadataStore;
		this.exSvc = exSvc;
	}

	public void readInfoAsync (final File file, final ContentItem item) {
		if (this.mediaMetadataStore == null) return;
		this.exSvc.submit(new ReadInfoJob(file, item, this.mediaMetadataStore));
	}

	private static class ReadInfoJob implements Runnable {

		private final File file;
		private final ContentItem item;
		private final MediaMetadataStore mediaMetadataStore;

		public ReadInfoJob (final File file, final ContentItem item, final MediaMetadataStore mediaMetadataStore) {
			this.file = file;
			this.item = item;
			this.mediaMetadataStore = mediaMetadataStore;
		}

		@Override
		public void run () {
			try {
				this.item.setDurationMillis(readDurationMillis());
			}
			catch (final Exception e) {
				LOG.warn("Failed to read duration: \"{}\" {}", this.file.getAbsolutePath(), e.toString());
			}
		}

		private long readDurationMillis () throws IOException, SQLException, InterruptedException {
			final long storedDurationMillis = this.mediaMetadataStore.readFileDurationMillis(this.file);
			if (storedDurationMillis > 0) return storedDurationMillis;

			final FfprobeInfo info = Ffprobe.inspect(this.file);
			final Long readDuration = info.getDurationMillis();
			if (readDuration != null && readDuration > 0) {
				this.mediaMetadataStore.storeFileDurationMillisAsync(this.file, readDuration);
				return readDuration;
			}

			LOG.warn("Failed to read duration: {}", this.file.getAbsolutePath());
			return 0;
		}

	}

}

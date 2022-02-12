package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.support.model.Res;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.ffmpeg.Ffprobe;
import com.vaguehope.dlnatoad.ffmpeg.FfprobeInfo;

public class MediaInfo {

	private static final Logger LOG = LoggerFactory.getLogger(MediaInfo.class);

	private final MediaDb mediaDb;
	private final ExecutorService exSvc;

	public MediaInfo () {
		this(null, null);
	}

	public MediaInfo (final MediaDb mediaDb, final ExecutorService exSvc) {
		this.mediaDb = mediaDb;
		this.exSvc = exSvc;
	}

	public void readInfoAsync (final File file, final Res res) {
		if (this.mediaDb == null) return;
		this.exSvc.submit(new ReadInfoJob(file, res, this.mediaDb));
	}

	private static class ReadInfoJob implements Runnable {

		private final File file;
		private final Res res;
		private final MediaDb mediaDb;

		public ReadInfoJob (final File file, final Res res, final MediaDb mediaDb) {
			this.file = file;
			this.res = res;
			this.mediaDb = mediaDb;
		}

		@Override
		public void run () {
			try {
				final long durationMillis = readDurationMillis();
				final long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis);
				this.res.setDuration(ModelUtil.toTimeString(durationSeconds));
			}
			catch (final Exception e) {
				LOG.warn("Failed to read duration: \"{}\" {}", this.file.getAbsolutePath(), e.toString());
			}
		}

		private long readDurationMillis () throws IOException, SQLException, InterruptedException {
			final long storedDurationMillis = this.mediaDb.readFileDurationMillis(this.file);
			if (storedDurationMillis > 0) return storedDurationMillis;

			final FfprobeInfo info = Ffprobe.inspect(this.file);
			final Long readDuration = info.getDurationMillis();
			if (readDuration != null && readDuration > 0) {
				this.mediaDb.storeFileDurationMillisAsync(this.file, readDuration);
				return readDuration;
			}

			LOG.warn("Failed to read duration: {}", this.file.getAbsolutePath());
			return 0;
		}

	}

}

package com.vaguehope.dlnatoad.util;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.util.Watcher.EventResult;
import com.vaguehope.dlnatoad.util.Watcher.EventType;
import com.vaguehope.dlnatoad.util.Watcher.FileListener;

public class ProgressLogFileListener implements FileListener {

	private static final long LOG_EVERY_NANOS = TimeUnit.MINUTES.toNanos(5);
	private static final long DISCARD_AFTER_NANOS = TimeUnit.MINUTES.toNanos(20);

	private static final Logger LOG = LoggerFactory.getLogger(ProgressLogFileListener.class);

	private final FileListener deligate;
	private final boolean verboseLog;

	public ProgressLogFileListener (final FileListener deligate, final boolean verboseLog) {
		this.deligate = deligate;
		this.verboseLog = verboseLog;
	}

	private final Object[] LOCK = new Object[]{};
	private volatile long startNanos = 0L;
	private volatile int fileCounter = 0;
	private volatile long byteCounter = 0L;
	private volatile long lastUpdateNanos = 0L;

	private void beforeFileProcessed (final File file) {
		synchronized (this.LOCK) {
			final long nowNanos = System.nanoTime();
			if (this.lastUpdateNanos == 0L || nowNanos - this.lastUpdateNanos > DISCARD_AFTER_NANOS) {
				this.lastUpdateNanos = nowNanos;
				this.startNanos = nowNanos;
				this.fileCounter = 0;
				this.byteCounter = 0;
			}
			this.fileCounter += 1;

			// FIXME move to afterFileProc?
			// files are now processed async so this does not work anymore.
			this.byteCounter += file.length();
		}
	}

	private void afterFileProcessed (final File file) {
		synchronized (this.LOCK) {
			final long nowNanos = System.nanoTime();
			if (nowNanos - this.lastUpdateNanos > LOG_EVERY_NANOS) {
					LOG.info("Indexed {} files ({}) in {} minutes.",
							this.fileCounter, FileHelper.readableFileSize(this.byteCounter),
							TimeUnit.NANOSECONDS.toMinutes(nowNanos - this.startNanos));
				this.lastUpdateNanos = nowNanos;
			}
		}
	}

	@Override
	public EventResult fileFound (final File rootDir, final File file, final EventType eventType, final Runnable onUsed) throws IOException {
		if (this.verboseLog) {
			LOG.info("Found: {}", file.getAbsolutePath());
		}

		beforeFileProcessed(file);
		final EventResult result = this.deligate.fileFound(rootDir, file, eventType, new Runnable() {
			@Override
			public void run() {
				afterFileProcessed(file);
				if (onUsed != null) onUsed.run();
			}
		});
		if (result == EventResult.ADDED) afterFileProcessed(file);
		return result;
	}

	@Override
	public EventResult fileModified (final File rootDir, final File file, final Runnable onUsed) throws IOException {
		if (this.verboseLog) {
			LOG.info("Modified: {}", file.getAbsolutePath());
		}

		beforeFileProcessed(file);
		final EventResult result = this.deligate.fileModified(rootDir, file, new Runnable() {
			@Override
			public void run() {
				afterFileProcessed(file);
				if (onUsed != null) onUsed.run();
			}
		});
		if (result == EventResult.ADDED) afterFileProcessed(file);
		return result;
	}

	@Override
	public void fileGone (final File file) throws IOException {
		this.deligate.fileGone(file);
	}

}

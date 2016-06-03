package com.vaguehope.dlnatoad.util;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.util.Watcher.EventType;
import com.vaguehope.dlnatoad.util.Watcher.FileListener;

public class ProgressLogFileListener implements FileListener {

	private static final long LOG_EVERY_NANOS = TimeUnit.MINUTES.toNanos(5);
	private static final long DISCARD_AFTER_NANOS = TimeUnit.MINUTES.toNanos(20);

	private static final Logger LOG = LoggerFactory.getLogger(ProgressLogFileListener.class);

	private final FileListener deligate;

	public ProgressLogFileListener (final FileListener deligate) {
		this.deligate = deligate;
	}

	private volatile long startNanos = 0L;
	private volatile int fileCounter = 0;
	private volatile long byteCounter = 0L;
	private volatile long lastUpdateNanos = 0L;

	private void beforeFileProcessed (final File file) {
		final long nowNanos = System.nanoTime();
		if (this.lastUpdateNanos == 0L || nowNanos - this.lastUpdateNanos > DISCARD_AFTER_NANOS) {
			this.lastUpdateNanos = nowNanos;
			this.startNanos = nowNanos;
		}
		this.fileCounter += 1;
		this.byteCounter += file.length();
	}

	private void afterFileProcessed (final File file) {
		final long nowNanos = System.nanoTime();
		if (nowNanos - this.lastUpdateNanos > LOG_EVERY_NANOS) {
				LOG.info("Indexed {} files ({}) in {} minutes.",
						this.fileCounter, FileHelper.readableFileSize(this.byteCounter),
						TimeUnit.NANOSECONDS.toMinutes(nowNanos - this.startNanos));
			this.lastUpdateNanos = nowNanos;
		}
	}

	@Override
	public void fileFound (final File rootDir, final File file, final EventType eventType) throws IOException {
		beforeFileProcessed(file);
		try {
			this.deligate.fileFound(rootDir, file, eventType);
		}
		finally {
			afterFileProcessed(file);
		}
	}

	@Override
	public void fileModified (final File rootDir, final File file) throws IOException {
		beforeFileProcessed(file);
		try {
			this.deligate.fileModified(rootDir, file);
		}
		finally {
			afterFileProcessed(file);
		}
	}

	@Override
	public void fileGone (final File file) throws IOException {
		this.deligate.fileGone(file);
	}

}

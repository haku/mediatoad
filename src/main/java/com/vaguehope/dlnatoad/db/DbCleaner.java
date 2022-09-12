package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentTree;

public class DbCleaner {

	private static final long START_DELAY_SECONDS = TimeUnit.MINUTES.toSeconds(5);
	private static final Logger LOG = LoggerFactory.getLogger(DbCleaner.class);

	private final ContentTree contentTree;
	private final MediaDb mediaDb;
	private final boolean verboseLog;

	public DbCleaner(final ContentTree contentTree, final MediaDb mediaDb, final boolean verboseLog) {
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
		this.verboseLog = verboseLog;
	}

	public void start(final ScheduledExecutorService schExSvc) {
		// Start via executor so we can be sure initial scan is done.
		// Add an extra delay as a fudge factor.
		schExSvc.submit(() -> {
			schExSvc.schedule(new Worker(), START_DELAY_SECONDS, TimeUnit.SECONDS);
		});
	}

	private class Worker implements Runnable {
		@Override
		public void run() {
			try {
				cleanDb();
			}
			catch (final Exception e) {
				LOG.error("Exception while cleaning DB.", e);
			}
		}
	}

	// Visible for testing.
	void cleanDb() throws SQLException, IOException {
		if (this.verboseLog) LOG.info("Running DB cleanup...");

		final Set<String> existingFiles = new HashSet<>();
		for (final ContentItem item : this.contentTree.getItems()) {
			existingFiles.add(item.getFile().getAbsolutePath());
		}

		final List<String> newlyMissing = new ArrayList<>();
		for (final String file : this.mediaDb.getAllFilesThatAreNotMarkedAsMissing()) {
			if (!existingFiles.contains(file) && !(new File(file).exists())) {
				newlyMissing.add(file);
				if (this.verboseLog) LOG.info("Missing: {}", file);
			}
		}

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			for (final String f : newlyMissing) {
				w.setFileMissing(f, true);
			}
		}
		LOG.info("Marked {} files as missing.", newlyMissing.size());
	}

}

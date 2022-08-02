package com.vaguehope.dlnatoad.importer;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.comparator.NameFileComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.WritableMediaDb;

public class MetadataImporter {

	private static final long DROPDIR_SCAN_INTERVAL_SECONDS = TimeUnit.HOURS.toSeconds(1);
	private static final Logger LOG = LoggerFactory.getLogger(MetadataImporter.class);

	private final File dropDir;
	private final MediaDb mediaDb;
	private final AtomicLong countOfImportedTags = new AtomicLong(0L);

	public MetadataImporter(final File dropDir, final MediaDb mediaDb) {
		this.dropDir = dropDir;
		this.mediaDb = mediaDb;
	}

	public long getCountOfImportedTags() {
		return this.countOfImportedTags.get();
	}

	public void start(final ScheduledExecutorService schExSvc) {
		// Start via executor so we can be sure IDs for all existing files have been generated.
		schExSvc.submit(() -> {
			schExSvc.scheduleWithFixedDelay(new ImporterWorker(), 0, DROPDIR_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
		});
		LOG.info("Watching drop directory every {} seconds: {}", DROPDIR_SCAN_INTERVAL_SECONDS, this.dropDir.getAbsolutePath());
	}

	private class ImporterWorker implements Runnable {
		@Override
		public void run() {
			try {
				processDropDir();
			}
			catch (final Exception e) {
				LOG.error("Exception while processing drop dir.", e);
			}
		}
	}

	// Visible for testing.
	void processDropDir() throws SQLException, IOException {
		final File[] files = this.dropDir.listFiles();
		Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);  // Make somewhat predictable.
		for (final File file : files) {
			if (!file.isFile()) continue;
			if (!file.getName().endsWith(MetadataDump.NEW_FILE_EXTENSION)) continue;
			if (!file.canWrite()) {
				LOG.warn("Drop file needs to be writable so it can be renamed after processing: {}", file.getAbsolutePath());
				continue;
			}
			try {
				final MetadataDump md = MetadataDump.readFile(file);
				importMetadataDump(md);
				renameDropFile(file, MetadataDump.PROCESSED_FILE_EXTENSION);
			}
			catch (final Exception e) {
				LOG.warn("Failed to process drop file {}: {}", file.getAbsolutePath(), e.toString());
				renameDropFile(file, MetadataDump.FAILED_FILE_EXTENSION);
			}
		}
	}

	private void importMetadataDump(final MetadataDump md) throws SQLException, IOException {
		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			for (final HashAndTags hat : md.getHashAndTags()) {
				final String cid = this.mediaDb.canonicalIdForHash(hat.getSha1().toString(16));
				if (cid == null) continue;
				for (final String tag : hat.getTags()) {
					if (w.addTag(cid, tag, System.currentTimeMillis())) {
						this.countOfImportedTags.incrementAndGet();
					}
				}
			}
		}
	}

	private static void renameDropFile(final File file, String suffix) throws IOException {
		final File newFile = new File(file.getAbsolutePath() + suffix);
		FileUtils.moveFile(file, newFile);
	}

}

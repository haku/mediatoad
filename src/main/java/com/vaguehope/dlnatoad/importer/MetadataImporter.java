package com.vaguehope.dlnatoad.importer;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.importer.HashAndTags.ImportedTag;
import com.vaguehope.dlnatoad.util.Time;

public class MetadataImporter {

	private static final long DROPDIR_SCAN_INTERVAL_SECONDS = TimeUnit.HOURS.toSeconds(1);
	private static final Logger LOG = LoggerFactory.getLogger(MetadataImporter.class);

	private final File dropDir;
	private final MediaDb mediaDb;
	private boolean verboseLog;
	private final Time time;
	private final AtomicLong countOfImportedTags = new AtomicLong(0L);

	public MetadataImporter(final File dropDir, final MediaDb mediaDb, boolean verboseLog) {
		this(dropDir, mediaDb, verboseLog, Time.DEFAULT);
	}

	protected MetadataImporter(final File dropDir, final MediaDb mediaDb, final boolean verboseLog, final Time time) {
		this.dropDir = Objects.requireNonNull(dropDir, "dropDir");
		this.mediaDb = Objects.requireNonNull(mediaDb, "mediaDb");
		this.verboseLog = verboseLog;
		this.time = time;
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
		if (this.verboseLog) LOG.info("Importing {} files for drop dir...", files.length);
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
				final long changeCount = importMetadataDump(md);
				this.countOfImportedTags.addAndGet(changeCount);
				renameDropFile(file, MetadataDump.PROCESSED_FILE_EXTENSION);
				LOG.info("Successfully imported {} changes from drop file: {}", changeCount, file);
			}
			catch (final Exception e) {
				LOG.warn("Failed to process drop file {}: {}", file.getAbsolutePath(), e);
				renameDropFile(file, MetadataDump.FAILED_FILE_EXTENSION);
			}
		}
	}

	private long importMetadataDump(final MetadataDump md) throws SQLException, IOException {
		long changeCount = 0;
		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			for (final HashAndTags hat : md.getHashAndTags()) {
				final String cid = w.canonicalIdForHash(hat.getSha1().toString(16));
				if (cid == null) continue;
				for (final ImportedTag tag : hat.getTags()) {
					if (tag == null) throw new IllegalStateException("Null tag in list.");
					final String cls = StringUtils.trimToEmpty(tag.getCls());
					final boolean modified;
					if (tag.getMod() != 0) {
						modified = w.mergeTag(cid, tag.getTag(), cls, tag.getMod(), tag.isDel());
						if (this.verboseLog && modified) LOG.info("{} Merged: {} {}", changeCount, hat.getSha1().toString(16), tag);
					}
					else if (!tag.isDel()) {
						modified = w.addTagIfNotDeleted(cid, tag.getTag(), cls, this.time.now());
						if (this.verboseLog && modified) LOG.info("{} Added new: {} {}", changeCount, hat.getSha1().toString(16), tag);
					}
					else {
						continue;
					}
					if (modified) changeCount += 1;
				}
			}
		}
		return changeCount;
	}

	private static void renameDropFile(final File file, String suffix) throws IOException {
		final File newFile = new File(file.getAbsolutePath() + suffix);
		FileUtils.moveFile(file, newFile);
	}

}

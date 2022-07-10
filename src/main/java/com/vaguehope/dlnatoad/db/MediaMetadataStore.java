package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.media.MediaIdCallback;

public class MediaMetadataStore {

	private static final int COMMIT_DURATIONS_INTERVAL_SECONDS = 30;
	private static final Logger LOG = LoggerFactory.getLogger(MediaMetadataStore.class);

	private final BlockingQueue<FileAndDuration> storeDuraionQueue = new LinkedBlockingQueue<>();

	private final MediaDb mediaDb;
	private final boolean verboseLog;

	public MediaMetadataStore(final MediaDb mediaDb, final ScheduledExecutorService exSvc, final boolean verboseLog) {
		this(mediaDb, exSvc, verboseLog, COMMIT_DURATIONS_INTERVAL_SECONDS);
	}

	public MediaMetadataStore(final MediaDb mediaDb, final ScheduledExecutorService exSvc, final boolean verboseLog, final int commitDelaySeconds) {
		this.mediaDb = mediaDb;
		this.verboseLog = verboseLog;
		exSvc.scheduleWithFixedDelay(new DurationBatchWriter(), commitDelaySeconds, commitDelaySeconds, TimeUnit.SECONDS);
	}

	public void idForFile (final File file, final MediaIdCallback callback, final ExecutorService exSvc) throws SQLException, IOException {
		if (!file.isFile()) throw new IOException("Not a file: " + file.getAbsolutePath());

		final FileData oldFileData = this.mediaDb.readFileData(file);
		if (oldFileData == null || !oldFileData.upToDate(file)) {
			// Whatever needs doing, it might take a while and oldFileData may be out of date by the time the work
			// runs, so check everything closer to the time.  This is still a check-and-set and not really thread
			// safe, but given the executor is supposed to single threaded this should work out ok.
			exSvc.submit(new Runnable() {
				@Override
				public void run() {
					try {
						addOrUpdateFileData(file, callback);
					}
					catch (final Exception e) {
						if (e instanceof IOException) {
							callback.onError((IOException) e);
						}
						else {
							callback.onError(new IOException(e));
						}
					}
				}
			});
		}
		else {
			final String id = canonicaliseAndStoreId(oldFileData);
			callback.onResult(id);
		}
	}

	private void addOrUpdateFileData(final File file, final MediaIdCallback callback) throws SQLException, IOException {
		final FileData oldFileData = this.mediaDb.readFileData(file);
		final String id;
		if (oldFileData == null) {
			final FileData newFileData = generateNewFileData(file);
			id = canonicaliseAndStoreId(newFileData);
		}
		else if (!oldFileData.upToDate(file)) {
			final FileData updatedFileData = generateUpdatedFileData(file, oldFileData);
			id = canonicaliseAndStoreId(updatedFileData);
		}
		else {
			id = canonicaliseAndStoreId(oldFileData);
		}
		callback.onResult(id);
	}

	private String canonicaliseAndStoreId(final FileData fileData) throws SQLException {
		String id = this.mediaDb.canonicalIdForHash(fileData.getHash());
		if (id == null) {
			id = fileData.getId();
			this.mediaDb.storeCanonicalId(fileData.getHash(), id);
		}
		return id;
	}

	private FileData generateNewFileData(final File file) throws IOException, SQLException {
		FileData fileData;
		fileData = FileData.forFile(file);  // Slow.

		final Collection<FileAndData> otherFiles = missingFilesWithHash(fileData.getHash());
		final Set<String> otherIds = distinctIds(otherFiles);
		if (otherIds.size() == 1) {
			fileData = fileData.withId(otherIds.iterator().next());
		}
		else {
			fileData = fileData.withId(newUnusedId());
			otherFiles.clear(); // Did not merge, so do not remove.
		}

		this.mediaDb.storeFileData(file, fileData);
		removeFiles(otherFiles);

		if (this.verboseLog) {
			LOG.info("New [merged={}]: {}",
					otherFiles.size(),
					file.getAbsolutePath());
		}

		return fileData;
	}

	private FileData generateUpdatedFileData(final File file, FileData fileData) throws SQLException, IOException {
		final long prevModified = fileData.getModified();
		final String prevHash = fileData.getHash();
		final String prevHashCanonicalId = this.mediaDb.canonicalIdForHash(prevHash);
		fileData = FileData.forFile(file).withId(fileData.getId());  // Slow.

		Collection<FileAndData> otherFiles = Collections.emptySet();
		if (prevHashCanonicalId != null && !prevHashCanonicalId.equals(fileData.getId())) {
			otherFiles = this.mediaDb.filesWithHash(prevHash);
			excludeFile(otherFiles, file); // Remove self.

			if (allMissing(otherFiles)) {
				fileData = fileData.withNewId(prevHashCanonicalId);
			}
			else {
				otherFiles.clear(); // Did not merge, so do not remove.
			}
		}

		this.mediaDb.updateFileData(file, fileData);
		removeFiles(otherFiles);

		if (this.verboseLog) {
			LOG.info("Updated [merged={} hash={} mod={}]: {}",
					otherFiles.size(),
					prevHash.equals(fileData.getHash()) ? "same" : "changed",
					prevModified == fileData.getModified() ? "same" : prevModified + "-->" + fileData.getModified(),
					file.getAbsolutePath());
		}

		return fileData;
	}

	public long readFileDurationMillis (final File file) throws SQLException {
		return this.mediaDb.readDurationCheckingFileSize(file.getAbsolutePath(), file.length());
	}

	public void storeFileDurationMillisAsync (final File file, final long duration) throws SQLException, InterruptedException {
		this.storeDuraionQueue.put(new FileAndDuration(file, duration));
	}

	private class DurationBatchWriter implements Runnable {

		@Override
		public void run() {
			try {
				final List<FileAndDuration> todo = new ArrayList<>();
				MediaMetadataStore.this.storeDuraionQueue.drainTo(todo);
				if (todo.size() > 0) {
					try (final WritableMediaDb w = MediaMetadataStore.this.mediaDb.getWritable()) {
						w.storeDurations(todo);
					}
					LOG.info("Batch duration write for {} files.", todo.size());
				}
			}
			catch (final Exception e) {
				LOG.error("Scheduled batch duration writer error.", e);
			}
		}

	}

	private Collection<FileAndData> missingFilesWithHash (final String hash) throws SQLException {
		final Collection<FileAndData> files = this.mediaDb.filesWithHash(hash);
		excludeFilesThatStillExist(files);
		return files;
	}

	private String newUnusedId () throws SQLException {
		while (true) {
			final String id = UUID.randomUUID().toString();
			if (this.mediaDb.hashesForId(id).size() < 1) return id;
			LOG.warn("Discarding colliding random UUID: {}", id);
		}
	}

	protected void removeFiles (final Collection<FileAndData> files) throws SQLException {
		for (final FileAndData file : files) {
			this.mediaDb.removeFile(file.getFile());
		}
	}

	private static void excludeFilesThatStillExist (final Collection<FileAndData> files) {
		for (final Iterator<FileAndData> i = files.iterator(); i.hasNext();) {
			if (i.next().getFile().exists()) i.remove();
		}
	}

	private static void excludeFile (final Collection<FileAndData> files, final File file) {
		final int startSize = files.size();
		for (final Iterator<FileAndData> i = files.iterator(); i.hasNext();) {
			if (file.equals(i.next().getFile())) i.remove();
		}
		if (files.size() != startSize - 1) throw new IllegalStateException("Expected to only remove one item from list.");
	}

	private static Set<String> distinctIds (final Collection<FileAndData> files) {
		final Set<String> ids = new HashSet<>();
		for (final FileAndData f : files) {
			ids.add(f.getData().getId());
		}
		return ids;
	}

	private static boolean allMissing (final Collection<FileAndData> files) {
		for (final FileAndData file : files) {
			if (file.getFile().exists()) return false;
		}
		return true;
	}


}

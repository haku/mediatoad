package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.media.MediaIdCallback;

public class MediaMetadataStore {

	private static final long MEDIA_ID_BATCH_NANOS = TimeUnit.SECONDS.toNanos(10);
	private static final int DURATION_WRITE_INTERVAL_SECONDS = 30;
	private static final Logger LOG = LoggerFactory.getLogger(MediaMetadataStore.class);

	private final BlockingQueue<FileAndIdCallback> fileIdQueue = new LinkedBlockingQueue<>();
	private final AtomicBoolean fileIdWorkerRunning = new AtomicBoolean(false);
	private final BlockingQueue<FileAndDuration> storeDuraionQueue = new LinkedBlockingQueue<>();

	private final MediaDb mediaDb;
	private final ScheduledExecutorService exSvc;
	private final boolean verboseLog;

	public MediaMetadataStore(final MediaDb mediaDb, final ScheduledExecutorService exSvc, final boolean verboseLog) {
		this.mediaDb = mediaDb;
		this.exSvc = exSvc;
		this.verboseLog = verboseLog;
		exSvc.scheduleWithFixedDelay(new DurationWorker(), 0, DURATION_WRITE_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public MediaDb getMediaDb() {
		return this.mediaDb;
	}

	public void idForFile(final File file, final MediaIdCallback callback) throws IOException, InterruptedException {
		if (!file.isFile()) throw new IOException("Not a file: " + file.getAbsolutePath());
		this.fileIdQueue.put(new FileAndIdCallback(file, callback));
		scheduleFileIdBatchIfNeeded();
	}

	private void scheduleFileIdBatchIfNeeded() {
		if (this.fileIdWorkerRunning.compareAndSet(false, true)) {
			this.exSvc.execute(new FileIdWorker());
		}
	}

	private class FileIdWorker implements Runnable {
		@Override
		public void run() {
			try {
				processFileIdQueue();
			}
			catch (final Exception e) {
				LOG.error("Exception while processing file ID queue.", e);
			}
		}
	}

	private void processFileIdQueue() throws SQLException, IOException {
		final long startTime = System.nanoTime();
		int count = 0;
		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			FileAndIdCallback f;
			do {
				f = this.fileIdQueue.poll();
				if (f != null) {
					processFileIdRequest(w, f);
					count += 1;
				}
			}
			while (f != null && System.nanoTime() - startTime < MEDIA_ID_BATCH_NANOS);
			this.fileIdWorkerRunning.compareAndSet(true, false);
			// we have said we are not running anymore, any new work added to the queue
			// will add a new batch.  if there is any work still on the queue, schedule a
			// a batch to cover that.
			if (this.fileIdQueue.size() > 0) {
				scheduleFileIdBatchIfNeeded();
			}
		}
		LOG.info("Batch ID generation for {} files.", count);
	}

	private void processFileIdRequest(final WritableMediaDb w, final FileAndIdCallback f) {
		try {
			addOrUpdateFileData(w, f.getFile(), f.getCallback());
		}
		catch (final Exception e) {
			if (e instanceof IOException) {
				f.getCallback().onError((IOException) e);
			}
			else {
				f.getCallback().onError(new IOException(e));
			}
		}
	}

	private void addOrUpdateFileData(final WritableMediaDb w, final File file, final MediaIdCallback callback) throws SQLException, IOException {
		final FileData oldFileData = w.readFileData(file);
		final String id;
		if (oldFileData == null) {
			final FileData newFileData = generateNewFileData(w, file);
			id = canonicaliseAndStoreId(w, newFileData);
		}
		// If file does not exist, return what we have so the old ID can be used to remove the file from memory.
		else if (file.exists() && !oldFileData.upToDate(file)) {
			final FileData updatedFileData = generateUpdatedFileData(w, file, oldFileData);
			id = canonicaliseAndStoreId(w, updatedFileData);
		}
		else {
			id = canonicaliseAndStoreId(w, oldFileData);
		}
		callback.onResult(id);
	}

	private static String canonicaliseAndStoreId(final WritableMediaDb w, final FileData fileData) throws SQLException {
		String id = w.canonicalIdForHash(fileData.getHash());
		if (id == null) {
			id = fileData.getId();
			w.storeCanonicalId(fileData.getHash(), id);
		}
		return id;
	}

	private FileData generateNewFileData(final WritableMediaDb w, final File file) throws IOException, SQLException {
		FileData fileData = FileData.forFile(file); // Slow.
		Collection<FileAndData> filesToRemove = null;

		// A preexisting ID will only be used only if no other files with that hash still exist.
		// Otherwise a new ID will be generated and stored in the files table.
		final Collection<FileAndData> otherFiles = w.filesWithHash(fileData.getHash());
		excludeFilesThatStillExist(otherFiles);
		final Set<String> otherIds = distinctIds(otherFiles);
		if (otherIds.size() == 1) {
			fileData = fileData.withId(otherIds.iterator().next());
			filesToRemove = otherFiles;
		}
		else {
			fileData = fileData.withId(newUnusedId(w));
		}

		w.storeFileData(file, fileData);
		if (filesToRemove != null) {
			removeFiles(w, filesToRemove);
		}

		if (this.verboseLog) {
			LOG.info("New [merged={}]: {}",
					filesToRemove != null ? filesToRemove.size() : 0,
					file.getAbsolutePath());
		}
		return fileData;
	}

	private FileData generateUpdatedFileData(final WritableMediaDb w, final File file, final FileData oldFileData) throws SQLException, IOException {
		FileData fileData = FileData.forFile(file).withId(oldFileData.getId()); // Slow.
		Collection<FileAndData> filesToRemove = null;

		// ID from hashes table will be copied into files table only if all other files with that hash are missing.
		// Otherwise ID in files table with be unchanged.
		final String prevHashCanonicalId = w.canonicalIdForHash(oldFileData.getHash());
		if (prevHashCanonicalId != null && !prevHashCanonicalId.equals(oldFileData.getId())) {
			final Collection<FileAndData> otherFiles = w.filesWithHash(oldFileData.getHash());
			excludeFile(otherFiles, file); // Remove self.
			if (allMissing(otherFiles)) {
				fileData = fileData.withNewId(prevHashCanonicalId);
				filesToRemove = otherFiles;
			}
		}

		w.updateFileData(file, fileData);
		if (filesToRemove != null) {
			removeFiles(w, filesToRemove);
		}

		if (this.verboseLog) {
			LOG.info("Updated [merged={} hash={} mod={}]: {}",
					filesToRemove != null ? filesToRemove.size() : 0,
					oldFileData.getHash().equals(fileData.getHash()) ? "same" : "changed",
					oldFileData.getModified() == fileData.getModified() ? "same" : oldFileData.getModified() + "-->" + fileData.getModified(),
					file.getAbsolutePath());
		}
		return fileData;
	}

	private static String newUnusedId(final WritableMediaDb w) throws SQLException {
		while (true) {
			final String id = UUID.randomUUID().toString();
			if (w.hashesForId(id).size() < 1) return id;
			LOG.warn("Discarding colliding random UUID: {}", id);
		}
	}

	protected void removeFiles(final WritableMediaDb w, final Collection<FileAndData> files) throws SQLException {
		for (final FileAndData file : files) {
			w.removeFile(file.getFile());
		}
	}

	private static void excludeFilesThatStillExist(final Collection<FileAndData> files) {
		for (final Iterator<FileAndData> i = files.iterator(); i.hasNext();) {
			if (i.next().getFile().exists()) i.remove();
		}
	}

	private static void excludeFile(final Collection<FileAndData> files, final File file) {
		final int startSize = files.size();
		for (final Iterator<FileAndData> i = files.iterator(); i.hasNext();) {
			if (file.equals(i.next().getFile())) i.remove();
		}
		if (files.size() != startSize - 1) throw new IllegalStateException("Expected to only remove one item from list.");
	}

	private static Set<String> distinctIds(final Collection<FileAndData> files) {
		final Set<String> ids = new HashSet<>();
		for (final FileAndData f : files) {
			ids.add(f.getData().getId());
		}
		return ids;
	}

	private static boolean allMissing(final Collection<FileAndData> files) {
		for (final FileAndData file : files) {
			if (file.getFile().exists()) return false;
		}
		return true;
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	public long readFileDurationMillis(final File file) throws SQLException {
		return this.mediaDb.readDurationCheckingFileSize(file.getAbsolutePath(), file.length());
	}

	public void storeFileDurationMillisAsync(final File file, final long duration) throws SQLException, InterruptedException {
		this.storeDuraionQueue.put(new FileAndDuration(file, duration));
	}

	private class DurationWorker implements Runnable {

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

}

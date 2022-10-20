package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
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
import com.vaguehope.dlnatoad.util.HashHelper;

public class MediaMetadataStore {

	private static final long FILE_BATCH_START_DELAY_MILLIS = 100;  // Yield to other activities / DB writers.
	private static final long FILE_BATCH_MAX_DURATION_NANOS = TimeUnit.SECONDS.toNanos(10);
	private static final int DURATION_WRITE_INTERVAL_SECONDS = 30;
	private static final Logger LOG = LoggerFactory.getLogger(MediaMetadataStore.class);

	private final BlockingQueue<FileTask> fileQueue = new LinkedBlockingQueue<>();
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

	public void idForFile(final File file, final BigInteger auth, final MediaIdCallback callback) throws IOException, InterruptedException {
		if (!file.isFile()) throw new IOException("Not a file: " + file.getAbsolutePath());
		this.fileQueue.put(new FileTask(file, auth, callback));
		scheduleFileIdBatchIfNeeded();
	}

	public void fileGone(final File file) {
		try {
			this.fileQueue.put(new FileTask(file));
			scheduleFileIdBatchIfNeeded();
		}
		catch (final InterruptedException e) {
			LOG.warn("Interupted while waiting to put file gone task in queue.");
		}
	}

	public void putCallbackInQueue(final Runnable callback) {
		try {
			this.fileQueue.put(new FileTask(callback));
			scheduleFileIdBatchIfNeeded();
		}
		catch (final InterruptedException e) {
			LOG.warn("Interupted while waiting to put generic callback queue.");
		}
	}

	private void scheduleFileIdBatchIfNeeded() {
		if (this.fileIdWorkerRunning.compareAndSet(false, true)) {
			this.exSvc.schedule(new FileWorker(), FILE_BATCH_START_DELAY_MILLIS, TimeUnit.MILLISECONDS);
		}
	}

	private class FileWorker implements Runnable {
		@Override
		public void run() {
			try {
				processFileQueue();
			}
			catch (final Exception e) {
				LOG.error("Exception while processing file ID queue.", e);
			}
		}
	}

	private void processFileQueue() throws SQLException, IOException {
		final long startTime = System.nanoTime();
		int count = 0;
		Runnable genericCallback = null;

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			FileTask f = null;
			do {
				try {
					f = this.fileQueue.poll(10, TimeUnit.MILLISECONDS);
				}
				catch (InterruptedException e) {/* ignore */}
				if (f != null) {
					genericCallback = f.getGenericCallback();
					if (genericCallback != null) break;
					processFile(w, f);
					count += 1;
				}
			}
			while (f != null && System.nanoTime() - startTime < FILE_BATCH_MAX_DURATION_NANOS);
			this.fileIdWorkerRunning.compareAndSet(true, false);
			// we have said we are not running anymore, any new work added to the queue
			// will add a new batch.  if there is any work still on the queue, schedule a
			// a batch to cover that.
			if (this.fileQueue.size() > 0) {
				scheduleFileIdBatchIfNeeded();
			}
		}
		LOG.info("Batch file metadata write for {} files.", count);

		if (genericCallback != null) {
			genericCallback.run();
		}
	}

	private void processFile(final WritableMediaDb w, final FileTask f) {
		try {
			switch (f.getAction()) {
			case ID:
				addOrUpdateFileData(w, f.getFile(), f.getAuth(), f.getCallback());
				break;
			case GONE:
				// This is best effort as the file might have already been merged into another depending on message order.
				w.setFileMissing(f.getFile().getAbsolutePath(), true, /* dbMustChange= */false);
				break;
			default:
				LOG.error("Task missing action: {}", f);
			}
		}
		catch (final Exception e) {
			final MediaIdCallback callback = f.getCallback();
			if (callback != null) {
				if (e instanceof IOException) {
					callback.onError((IOException) e);
				}
				else {
					callback.onError(new IOException(e));
				}
			}
			else {
				LOG.error("File task {} failed.", f, e);
			}
		}
	}

	private void addOrUpdateFileData(final WritableMediaDb w, final File file, final BigInteger auth, final MediaIdCallback callback) throws SQLException, IOException {
		final FileData oldFileData = w.readFileData(file);
		final String id;
		if (oldFileData == null) {
			final FileData newFileData = generateNewFileData(w, file);
			id = canonicaliseAndStoreId(w, newFileData);
		}
		else if (file.exists() && !oldFileData.upToDate(file)) {
			final FileData updatedFileData = generateUpdatedFileData(w, file, oldFileData);
			id = canonicaliseAndStoreId(w, updatedFileData);
		}
		else {
			if (file.exists()) {
				// File has not changed but missing flag needs unsetting.
				if (oldFileData.isMissing()) {
					w.setFileMissing(file.getAbsolutePath(), false);
				}

				// Back fill MD5 if needed.
				if (oldFileData.getMd5() == null) {
					final String md5 = HashHelper.md5(file).toString(16);
					w.updateFileData(file, oldFileData.withMd5(md5));
				}
			}

			// Return what we have even if file does not exist so the old ID can be used to remove the file from memory.
			id = canonicaliseAndStoreId(w, oldFileData);
		}
		if (oldFileData == null || !oldFileData.hasAuth(auth)) {
			w.updateFileAuth(file, auth);
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
		Collection<FileAndId> filesToRemove = null;

		// A preexisting ID will only be used only if no other files with that hash still exist.
		// Otherwise a new ID will be generated and stored in the files table.
		final Collection<FileAndId> otherFiles = w.filesWithHash(fileData.getHash());
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
		Collection<FileAndId> filesToRemove = null;

		// ID from hashes table will be copied into files table only if all other files with that hash are missing.
		// Otherwise ID in files table with be unchanged.
		final String prevHashCanonicalId = w.canonicalIdForHash(oldFileData.getHash());
		if (prevHashCanonicalId != null && !prevHashCanonicalId.equals(oldFileData.getId())) {
			final Collection<FileAndId> otherFiles = w.filesWithHash(oldFileData.getHash());
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

	protected void removeFiles(final WritableMediaDb w, final Collection<FileAndId> files) throws SQLException {
		for (final FileAndId file : files) {
			w.removeFile(file.getFile());
		}
	}

	private static void excludeFilesThatStillExist(final Collection<FileAndId> files) {
		for (final Iterator<FileAndId> i = files.iterator(); i.hasNext();) {
			if (i.next().getFile().exists()) i.remove();
		}
	}

	private static void excludeFile(final Collection<FileAndId> files, final File file) {
		final int startSize = files.size();
		for (final Iterator<FileAndId> i = files.iterator(); i.hasNext();) {
			if (file.equals(i.next().getFile())) i.remove();
		}
		if (files.size() != startSize - 1) throw new IllegalStateException("Expected to only remove one item from list.");
	}

	private static Set<String> distinctIds(final Collection<FileAndId> files) {
		final Set<String> ids = new HashSet<>();
		for (final FileAndId f : files) {
			ids.add(f.getId());
		}
		return ids;
	}

	private static boolean allMissing(final Collection<FileAndId> files) {
		for (final FileAndId file : files) {
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

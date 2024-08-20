package com.vaguehope.dlnatoad.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.util.TreeWalker.Hiker;

import io.prometheus.metrics.core.datapoints.CounterDataPoint;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.CounterWithCallback;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public class Watcher {

	public enum EventType {
		SCAN,
		NOTIFY,
	}

	public enum EventResult {
		ADDED,
		NOT_ADDED,
		NOT_SURE_YET,  // If it is later used onUsed will be run.
	}

	public interface FileListener {
		EventResult fileFound (File rootDir, File file, EventType eventType, Runnable onUsed) throws IOException;
		EventResult fileModified (final File rootDir, File file, Runnable onUsed) throws IOException;
		void fileGone (File file, boolean isDir) throws IOException;
	}

	private static final Counter FILES_FOUND_METRIC = Counter.builder()
			.name("files_found")
			.labelNames("event")
			.help("count of files found, grouped by how they were found")
			.register();
	private static final CounterDataPoint FILES_FOUND_SCAN_METRIC = FILES_FOUND_METRIC.labelValues("scan");
	private static final CounterDataPoint FILES_FOUND_NOTIFY_METRIC = FILES_FOUND_METRIC.labelValues("notify");

	private static final Counter FILES_GONE_METRIC = Counter.builder()
			.name("files_gone")
			.help("count of files deleted or moved")
			.register();

	// NOTE: this will fail is more than once instance of Watcher exists.
	// if that is ever needed, will need to keep track of instances and add queue depths together.
	private final CounterWithCallback filesWaitingMetric = CounterWithCallback.builder()
			.name("files_waiting")
			.help("count of files that are inaccessable or very recently modified.")
			.callback((cb) -> cb.call(this.waitingFiles.size(), new String[] {}))
			.register();

	/**
	 * Do not fire modified event until file has stopped changing for this long.
	 */
	private static final long MODIFIED_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);

	/**
	 * Wait this long between checking if something is accessible.
	 */
	private static final long INACCESSABLE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(5);

	protected static final Logger LOG = LoggerFactory.getLogger(Watcher.class);

	private final List<File> roots;
	private final FileFilter filter;
	private final FileListener listener;
	private final Time time;
	private final long pollIntervalMillis;

	private final List<Runnable> onPrescanComplete = new CopyOnWriteArrayList<>();
	private final CountDownLatch prescanComplete = new CountDownLatch(1);
	private final AtomicLong watchEvents = new AtomicLong(0);

	private final WatchService watchService;
	private final Map<WatchKey, Path> watchKeys = new HashMap<>();
	private final Map<WatchKey, File> watchKeyRoots = new HashMap<>();
	private final Queue<WaitingFile> waitingFiles = new DelayQueue<>();

	private volatile boolean running = true;

	public Watcher (final List<File> roots, final FileFilter filter, final FileListener listener) throws IOException {
		this(roots, filter, listener, Time.DEFAULT, TimeUnit.SECONDS.toMillis(5));
	}

	@SuppressWarnings("resource")
	Watcher (final List<File> roots, final FileFilter filter, final FileListener listener, final Time time, final long pollIntervalMillis) throws IOException {
		this.roots = roots;
		this.filter = filter;
		this.listener = listener;
		this.time = time;
		this.pollIntervalMillis = pollIntervalMillis;
		this.watchService = FileSystems.getDefault().newWatchService();
	}

	public void run () throws IOException {
		try {
			long totalFiles = 0L;
			final long startTime = System.nanoTime();
			for (final File root : this.roots) {
				totalFiles += registerRecursive(root, root);
			}
			final long scanTime = System.nanoTime() - startTime;
			LOG.info("Found {} media files in {} seconds.", totalFiles, TimeUnit.NANOSECONDS.toSeconds(scanTime));

			this.prescanComplete.countDown();
			for (final Runnable l : this.onPrescanComplete) {
				l.run();
			}

			watch();
		}
		finally {
			LOG.error("Watcher terminated.");
			this.watchService.close();
		}
	}

	public void addPrescanCompleteListener(final Runnable l) {
		this.onPrescanComplete.add(l);
	}

	public void waitForPrescan(final long timeout, final TimeUnit unit) throws InterruptedException {
		this.prescanComplete.await(timeout, unit);
	}

	public long getWatchEventCount() {
		return this.watchEvents.get();
	}

	public void shutdown () {
		PrometheusRegistry.defaultRegistry.unregister(this.filesWaitingMetric);
		this.running = false;
	}

	protected void register (final File rootDir, final Path dir) throws IOException {
		if (!Files.isReadable(dir)) {
			LOG.debug("Waiting for access to register: {}", dir);
			this.waitingFiles.add(new WaitingFile(dir, rootDir, StandardWatchEventKinds.ENTRY_CREATE, this.time));
			return;
		}

		LOG.debug("Registering: {}", dir);
		final WatchKey watchKey = dir.register(this.watchService,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.ENTRY_DELETE);
		this.watchKeys.put(watchKey, dir);
		this.watchKeyRoots.put(watchKey, rootDir);
		LOG.debug("Register complete: {}", dir);
	}

	/**
	 * Returns number of files found during initial scan.
	 */
	private long registerRecursive (final File rootDir, final File dir) throws IOException {
		if (!dir.exists()) throw new FileNotFoundException("Unable to watch dir '" + dir + "' as it does not exist.");
		final RegisterRecursiveHiker hiker = new RegisterRecursiveHiker(this, rootDir);
		new TreeWalker(dir, this.filter, hiker).walk();
		return hiker.getTotalFiles();
	}

	private void watch () {
		while (this.running) {
			WaitingFile modFile;
			while ((modFile = this.waitingFiles.poll()) != null) {
				if (modFile.isReady()) {
					try {
						readReadyPath(modFile.getEventKind(), modFile.getPath(), modFile.getFile().isDirectory(), modFile.getRootDir());
					}
					catch (final Exception e) {
						LOG.warn("Failed to process waiting file that should have been ready: {} {}: {}",
								modFile.getEventKind().name(), modFile.getFile().getAbsolutePath(), e);
					}
				}
				else if (modFile.exists()) {
					LOG.info("File not ready: {}", modFile.getFile());
					this.waitingFiles.add(modFile.renew());
				}
			}

			final WatchKey key = this.watchService.poll();
			if (key == null) {
				try {
					TimeUnit.MILLISECONDS.sleep(this.pollIntervalMillis);
				}
				catch (final InterruptedException e) {
					LOG.debug("Interrupted, terminating watcher...");
					this.running = false;
					return;
				}
				continue;
			}

			final Path dir = this.watchKeys.get(key);
			if (dir == null) {
				LOG.error("WatchKey not known: {}", key);
				continue;
			}

			final File rootDir = this.watchKeyRoots.get(key);
			if (rootDir == null) {
				LOG.error("WatchKey root not found: {}", key);
				continue;
			}

			try {
				readWatchKey(rootDir, dir, key);
			}
			catch (final Exception e) { // NOSONAR
				LOG.warn("Failed to process '{}': {}", key, e.toString());
			}

			if (!key.reset()) {
				LOG.info("WatchKey no longer valid: {}", dir);
				this.watchKeys.remove(key);
				this.watchKeyRoots.remove(key);
				if (this.watchKeys.isEmpty()) {
					this.running = false;
					return;
				}
			}
		}
	}

	private void readWatchKey (final File rootDir, final Path dir, final WatchKey key) throws IOException {
		for (final WatchEvent<?> event : key.pollEvents()) {
			if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
				LOG.warn("Overflow WatchEvent!");
				continue;
			}

			final WatchEvent<Path> ev = cast(event);
			final Path path = dir.resolve(ev.context());
			LOG.debug("Event: {} {}", ev.kind().name(), path);

			// Files.isDirectory() will return false for deleted dirs.
			// containsValue() is a linear search.
			final boolean isDir = Files.isDirectory(path) || this.watchKeys.containsValue(path);

			// TODO ignore . files
			if (!isDir && !this.filter.accept(path.toFile())) {
				LOG.debug("Ignoring: {}", path);
				this.watchEvents.incrementAndGet();
				continue;
			}

			if (ev.kind() == StandardWatchEventKinds.ENTRY_CREATE
				|| ev.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
				if (!Files.isReadable(path)) {
					LOG.debug("Waiting for access: {}", path);
					this.waitingFiles.add(new WaitingFile(path, rootDir, ev.kind(), this.time)); // Wait for file to be accessible.
				}
				else if (Files.isDirectory(path)) {
					readReadyPath(ev.kind(), path, isDir, rootDir);
				}
				else {
					LOG.debug("Waiting for ready: {} {}", path, ev.kind());
					this.waitingFiles.add(new WaitingFile(path, rootDir, ev.kind(), this.time)); // Wait for the file to stop changing.
				}
			}
			else if (ev.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
				readReadyPath(ev.kind(), path, isDir, rootDir);
			}
			else {
				LOG.error("Unexpected event type: {}", ev.kind());
				return;
			}

			this.watchEvents.incrementAndGet();
		}
	}

	private void readReadyPath(final Kind<Path> kind, final Path path, boolean isDir, final File rootDir) throws IOException {
		if (Files.isDirectory(path) && kind == StandardWatchEventKinds.ENTRY_CREATE) {
			registerRecursive(rootDir, path.toFile());
		}
		else {
			callListener(kind, path.toFile(), isDir, rootDir, EventType.NOTIFY);
		}
	}

	protected void initialScanFiles (final List<File> files, final File rootDir) {
		final Kind<Path> kind = StandardWatchEventKinds.ENTRY_CREATE;
		for (final File file : files) {
			if (!file.canRead()) {
				LOG.debug("Waiting for access: {}", file);
				this.waitingFiles.add(new WaitingFile(file.toPath(), rootDir, kind, this.time)); // Wait for file to be accessible.
			}
			else {
				callListener(kind, file, file.isDirectory(), rootDir, EventType.SCAN);
			}
		}
	}

	private void callListener (final Kind<Path> kind, final File file, boolean isDir, final File rootDir, final EventType eventType) {
		LOG.debug("Calling listener: {} {}", file.getAbsolutePath(), kind);
		try {
			if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
				incFoundMetric(eventType, isDir);
				this.listener.fileFound(rootDir, file, eventType, null);
			}
			else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
				this.listener.fileModified(rootDir, file, null);
			}
			else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
				incGoneMetric(isDir);
				this.listener.fileGone(file, isDir);
			}
		}
		catch (final Exception e) { // NOSONAR do not allow errors to kill watcher.
			LOG.warn("Listener failed: {} {}: {}", kind.name(), file.getAbsolutePath(), e);
		}
	}

	private static void incFoundMetric(EventType eventType, boolean isDir) {
		if (isDir) return;
		switch (eventType) {
			case SCAN:
				FILES_FOUND_SCAN_METRIC.inc();
				return;
			case NOTIFY:
				FILES_FOUND_NOTIFY_METRIC.inc();
				return;
			default:
		}
	}

	private static void incGoneMetric(boolean isDir) {
		if (isDir) return;
		FILES_GONE_METRIC.inc();
	}

	@SuppressWarnings("unchecked")
	private static <T> WatchEvent<T> cast (final WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	private static class RegisterRecursiveHiker extends Hiker {

		private final Watcher host;
		private final File rootDir;
		private long totalFiles = 0L;

		protected RegisterRecursiveHiker (final Watcher host, final File rootDir) {
			this.host = host;
			this.rootDir = rootDir;
		}

		public long getTotalFiles () {
			return this.totalFiles;
		}

		@Override
		public void onDir (final File dir) throws IOException {
			this.host.register(this.rootDir, dir.toPath());
		}

		@Override
		public void onDirWithFiles (final File dir, final List<File> files) {
			this.host.initialScanFiles(files, this.rootDir);
			this.totalFiles += files.size();
		}

	}

	private static class WaitingFile implements Delayed {

		private final Path path;
		private final File file;
		private final File rootDir;
		private final Kind<Path> eventKind;
		private final Time time;

		private final long lastModifiedMillis;
		private final long readyAtTime;

		public WaitingFile (final Path path, final File rootDir, final Kind<Path> eventKind, final Time time) {
			this.path = path;
			this.file = path.toFile();
			this.rootDir = rootDir;
			this.eventKind = eventKind;
			this.time = time;

			this.lastModifiedMillis = this.file.lastModified();

			final long delay = Files.isReadable(path)
					? MODIFIED_TIMEOUT_NANOS
					: INACCESSABLE_TIMEOUT_NANOS;
			this.readyAtTime = delay + time.now();
		}

		public WaitingFile renew() {
			return new WaitingFile(this.path, this.rootDir, this.eventKind, this.time);
		}

		public boolean isReady () {
			return Files.isReadable(this.path) && this.file.lastModified() == this.lastModifiedMillis;
		}

		public boolean exists () {
			return Files.exists(this.path);
		}

		public Path getPath() {
			return this.path;
		}

		public File getFile () {
			return this.file;
		}

		public File getRootDir () {
			return this.rootDir;
		}

		public Kind<Path> getEventKind () {
			return this.eventKind;
		}

		@Override
		public int compareTo (final Delayed o) {
			if (o == this) return 0;
			final long d = (getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS));
			return (d == 0) ? 0 : ((d < 0) ? -1 : 1);
		}

		@Override
		public long getDelay (final TimeUnit unit) {
			return unit.convert(this.readyAtTime - this.time.now(), TimeUnit.NANOSECONDS);
		}

	}

}

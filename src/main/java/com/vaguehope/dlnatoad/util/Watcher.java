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
import java.util.Queue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.util.TreeWalker.Hiker;

public class Watcher {

	public enum EventType {
		SCAN,
		NOTIFY,
	}

	public interface FileListener {
		boolean fileFound (File rootDir, File file, EventType eventType) throws IOException;
		boolean fileModified (final File rootDir, File file) throws IOException;
		void fileGone (File file) throws IOException;
	}

	/**
	 * Do not fire modified event until file has stopped changing for this long.
	 */
	private static final long MODIFIED_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

	protected static final Logger LOG = LoggerFactory.getLogger(Watcher.class);

	private final List<File> roots;
	private final FileFilter filter;
	private final FileListener listener;
	private final WatchService watchService;
	private final HashMap<WatchKey, Path> watchKeys = new HashMap<>();
	private final HashMap<WatchKey, File> watchKeyRoots = new HashMap<>();
	private final Queue<ModifiedFile> modifiedFiles = new DelayQueue<>();

	private volatile boolean running = true;

	public Watcher (final List<File> roots, final FileFilter filter, final FileListener listener) throws IOException {
		this.roots = roots;
		this.filter = filter;
		this.listener = listener;
		this.watchService = FileSystems.getDefault().newWatchService();
	}

	public void run () throws IOException {
		try {
			long totalFiles = 0L;
			for (final File root : this.roots) {
				totalFiles += registerRecursive(root, root);
			}
			LOG.info("Found {} media files.", totalFiles);
			watch();
		}
		finally {
			LOG.error("Watcher terminated.");
			this.watchService.close();
		}
	}

	public void shutdown () {
		this.running = false;
	}

	protected void register (final File rootDir, final Path dir) throws IOException {
		final WatchKey watchKey = dir.register(this.watchService,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.ENTRY_DELETE);
		this.watchKeys.put(watchKey, dir);
		this.watchKeyRoots.put(watchKey, rootDir);
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
			ModifiedFile modFile;
			while ((modFile = this.modifiedFiles.poll()) != null) {
				if (modFile.isUnchanged()) {
					callListener(modFile.getEventKind(), modFile.getFile(), modFile.getRootDir(), EventType.NOTIFY);
				}
			}

			final WatchKey key = this.watchService.poll();
			if (key == null) {
				try {
					TimeUnit.SECONDS.sleep(1);
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
			LOG.debug("{} {}", ev.kind().name(), path);

			final File file = path.toFile();

			if (Files.isRegularFile(path)
					&& (ev.kind() == StandardWatchEventKinds.ENTRY_CREATE || ev.kind() == StandardWatchEventKinds.ENTRY_MODIFY)) {
				this.modifiedFiles.add(new ModifiedFile(file, rootDir, ev.kind())); // Wait for the file to stop changing.
			}
			else if (Files.isDirectory(path) && ev.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
				registerRecursive(rootDir, file);
			}
			else {
				callListener(ev.kind(), file, rootDir, EventType.NOTIFY);
			}
		}
	}

	protected void callListener (final Kind<Path> kind, final List<File> files, final File rootDir, final EventType eventType) {
		for (final File file : files) {
			callListener(kind, file, rootDir, eventType);
		}
	}

	protected void callListener (final Kind<Path> kind, final File file, final File rootDir, final EventType eventType) {
		try {
			if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
				this.listener.fileFound(rootDir, file, eventType);
			}
			else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
				this.listener.fileModified(rootDir, file);
			}
			else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
				this.listener.fileGone(file);
			}
		}
		catch (final Exception e) { // NOSONAR do not allow errors to kill watcher.
			LOG.warn("Listener failed: {} {}: {}", kind.name(), file.getAbsolutePath(), e);
		}
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
			this.host.callListener(StandardWatchEventKinds.ENTRY_CREATE, files, this.rootDir, EventType.SCAN);
			this.totalFiles += files.size();
		}

	}

	private static class ModifiedFile implements Delayed {

		private final File file;
		private final File rootDir;
		private final Kind<Path> eventKind;

		private final long lastModifiedMillis;
		private final long time;

		public ModifiedFile (final File file, final File rootDir, final Kind<Path> eventKind) {
			this.file = file;
			this.rootDir = rootDir;
			this.eventKind = eventKind;

			this.lastModifiedMillis = file.lastModified();
			this.time = MODIFIED_TIMEOUT_NANOS + now();
		}

		public boolean isUnchanged () {
			return this.file.lastModified() == this.lastModifiedMillis;
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
			return unit.convert(this.time - now(), TimeUnit.NANOSECONDS);
		}

		private static final long NANO_ORIGIN = System.nanoTime();

		private static long now () {
			return System.nanoTime() - NANO_ORIGIN;
		}

	}

}

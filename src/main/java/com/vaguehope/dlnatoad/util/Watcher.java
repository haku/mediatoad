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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.util.TreeWalker.Hiker;

public class Watcher {

	public enum EventType {
		SCAN,
		NOTIFY,
	}

	public interface FileListener {
		void fileFound (File file, EventType eventType);

		void fileGone (File file);
	}

	protected static final Logger LOG = LoggerFactory.getLogger(Watcher.class);

	private final List<File> roots;
	private final FileFilter filter;
	private final FileListener listener;
	private final WatchService watchService;
	private final HashMap<WatchKey, Path> watchKeys;

	private volatile boolean running = true;

	public Watcher (final List<File> roots, final FileFilter filter, final FileListener listener) throws IOException {
		this.roots = roots;
		this.filter = filter;
		this.listener = listener;
		this.watchService = FileSystems.getDefault().newWatchService();
		this.watchKeys = new HashMap<WatchKey, Path>();
	}

	public void run () throws IOException {
		try {
			long totalFiles = 0L;
			for (final File root : this.roots) {
				totalFiles += registerRecursive(root);
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

	protected void register (final Path dir) throws IOException {
		this.watchKeys.put(dir.register(this.watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE), dir);
	}

	/**
	 * Returns number of files found during initial scan.
	 */
	private long registerRecursive (final File root) throws IOException {
		if (!root.exists()) throw new FileNotFoundException("Unable to watch dir '" + root + "' as it does not exist.");
		final RegisterRecursiveHiker hiker = new RegisterRecursiveHiker(this);
		new TreeWalker(root, this.filter, hiker).walk();
		return hiker.getTotalFiles();
	}

	private void watch () {
		while (this.running) {
			final WatchKey key;
			try {
				key = this.watchService.take();
			}
			catch (final InterruptedException x) {
				LOG.debug("Interrupted, terminating watcher...");
				this.running = false;
				return;
			}

			final Path dir = this.watchKeys.get(key);
			if (dir == null) {
				LOG.error("WatchKey not known: {}", key);
				continue;
			}

			try {
				readWatchKey(dir, key);
			}
			catch (final Exception e) { // NOSONAR
				LOG.warn("Failed to process '{}': {}", key, e.toString());
			}

			if (!key.reset()) {
				LOG.info("WatchKey no longer valid: {}", dir);
				this.watchKeys.remove(key);
				if (this.watchKeys.isEmpty()) {
					this.running = false;
					return;
				}
			}
		}
	}

	private void readWatchKey (final Path dir, final WatchKey key) throws IOException {
		for (final WatchEvent<?> event : key.pollEvents()) {
			if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
				LOG.warn("Overflow WatchEvent!");
				continue;
			}

			final WatchEvent<Path> ev = cast(event);
			final Path path = dir.resolve(ev.context());
			LOG.debug("{} {}", ev.kind().name(), path);

			if (ev.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(path)) {
				registerRecursive(path.toFile());
			}

			callListener(ev.kind(), path.toFile(), EventType.NOTIFY);
		}
	}

	protected void callListener (final Kind<Path> kind, final List<File> files, final EventType eventType) {
		for (final File file : files) {
			callListener(kind, file, eventType);
		}
	}

	protected void callListener (final Kind<Path> kind, final File file, final EventType eventType) {
		try {
			if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
				this.listener.fileFound(file, eventType);
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
		private long totalFiles = 0L;

		private RegisterRecursiveHiker (final Watcher host) {
			this.host = host;
		}

		public long getTotalFiles () {
			return this.totalFiles;
		}

		@Override
		public void onDir (final File dir) throws IOException {
			this.host.register(dir.toPath());
		}

		@Override
		public void onDirWithFiles (final File dir, final List<File> files) {
			this.host.callListener(StandardWatchEventKinds.ENTRY_CREATE, files, EventType.SCAN);
			this.totalFiles += files.size();
		}

	}

}

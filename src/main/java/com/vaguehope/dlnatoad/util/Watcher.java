package com.vaguehope.dlnatoad.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private final FileListener listener;
	private final WatchService watchService;
	private final HashMap<WatchKey, Path> watchKeys;

	private volatile boolean running = true;

	public Watcher (final List<File> roots, final FileListener listener) throws IOException {
		this.roots = roots;
		this.listener = listener;
		this.watchService = FileSystems.getDefault().newWatchService();
		this.watchKeys = new HashMap<WatchKey, Path>();
	}

	public void run () throws IOException {
		try {
			for (final File root : this.roots) {
				registerRecursive(root);
			}
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

	private void registerRecursive (final File root) throws IOException {
		if (!root.exists()) throw new FileNotFoundException("Unable to watch dir '" + root + "' as it does not exist.");
		registerRecursive(root.toPath());
	}

	private void registerRecursive (final Path root) throws IOException {
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory (final Path dir, final BasicFileAttributes attrs) throws IOException {
				register(dir);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile (final Path file, final BasicFileAttributes attrs) throws IOException {
				callListener(StandardWatchEventKinds.ENTRY_CREATE, file, EventType.SCAN);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed (final Path file, final IOException exc) throws IOException {
				LOG.warn("File not readable {}: {}", file, exc);
				return FileVisitResult.CONTINUE;
			}
		});
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
				registerRecursive(path);
			}

			callListener(ev.kind(), path, EventType.NOTIFY);
		}
	}

	protected void callListener (final Kind<Path> kind, final Path path, final EventType eventType) {
		try {
			if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
				this.listener.fileFound(path.toFile(), eventType);
			}
			else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
				this.listener.fileGone(path.toFile());
			}
		}
		catch (final Exception e) { // NOSONAR do not allow errors to kill watcher.
			LOG.warn("Listener failed: {} {}: {}", kind.name(), path, e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> WatchEvent<T> cast (final WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

}

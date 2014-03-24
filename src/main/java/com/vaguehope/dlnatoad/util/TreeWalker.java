package com.vaguehope.dlnatoad.util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.commons.io.comparator.NameFileComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeWalker {

	private final List<File> roots;
	private final FileFilter fileFilter;
	private final Hiker hiker;

	public TreeWalker (final List<File> roots, final FileFilter fileFilter, final Hiker hiker) {
		this.roots = roots;
		this.fileFilter = fileFilter;
		this.hiker = hiker;
	}

	public void walk () throws IOException {
		final Queue<File> dirs = new LinkedList<File>();
		for (File root : this.roots) {
			dirs.add(root.getCanonicalFile());
		}

		while (!dirs.isEmpty()) {
			final File dir = dirs.poll();
			final File[] listFiles = dir.listFiles();
			if (listFiles != null) {
				Arrays.sort(listFiles, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
				List<File> keepFiles = null;
				for (final File file : listFiles) {
					final File cFile = file.getCanonicalFile();
					if (cFile.getName().startsWith(".")) {
						continue;
					}
					else if (cFile.isDirectory()) {
						dirs.add(cFile);
					}
					else if (cFile.isFile() && this.fileFilter.accept(cFile)) {
						if (keepFiles == null) keepFiles = new ArrayList<File>();
						keepFiles.add(cFile);
					}
				}
				if (keepFiles != null) {
					this.hiker.onDirWithFiles(dir, keepFiles);
					keepFiles = null;
				}
			}
			else {
				this.hiker.onUnreadableDir(dir);
			}
		}
	}

	public abstract static class Hiker {

		private static final Logger LOG = LoggerFactory.getLogger(TreeWalker.Hiker.class);

		public abstract void onDirWithFiles (File dir, List<File> files) throws IOException;

		public void onUnreadableDir (final File dir) {
			LOG.warn("Failed to read dir: {}", dir.getAbsolutePath());
		}

	}

}

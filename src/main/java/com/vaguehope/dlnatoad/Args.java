package com.vaguehope.dlnatoad;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

public class Args {

	@Option(name = "-t", aliases = { "--tree" }, metaVar = "<file>", usage = "file root dirs to scan, one per line.") private String treePath;
	@Option(name = "-d", aliases = { "--daemon" }, usage = "detach form terminal and run in bakground.") private boolean daemonise;
	@Argument(multiValued = true, metaVar = "DIR") private List<String> dirPaths;

	public List<File> getDirs () throws CmdLineException, IOException {
		final List<File> dirs = new ArrayList<File>();

		if (this.treePath != null && this.treePath.length() > 0) {
			final File treeFile = new File(this.treePath);
			if (!treeFile.exists()) throw new CmdLineException(null, "File not found: " + this.treePath);
			if (!treeFile.isFile()) throw new CmdLineException(null, "Not a file: " + this.treePath);
			for (final String line : FileUtils.readLines(treeFile)) {
				if (line.length() < 1 || line.startsWith("#")) continue;
				final File lineDir = new File(line);
				if (!lineDir.exists()) throw new CmdLineException(null, "Directory not found: " + line);
				if (!lineDir.isDirectory()) throw new CmdLineException(null, "Not a directory: " + line);
				dirs.add(lineDir);
			}
		}

		if (this.dirPaths != null && this.dirPaths.size() > 0) {
			final List<File> cliDirs = pathsToFiles(this.dirPaths);
			checkDirExist(cliDirs);
			dirs.addAll(dirs);
		}

		if (dirs.size() < 1) dirs.add(new File("."));

		return dirs;
	}

	public boolean isDaemonise() {
		return this.daemonise;
	}

	private static List<File> pathsToFiles (final List<String> paths) {
		List<File> files = new ArrayList<File>();
		for (String path : paths) {
			files.add(new File(path));
		}
		return files;
	}

	private static void checkDirExist (final List<File> files) throws CmdLineException {
		for (File file : files) {
			if (!file.exists() || !file.isDirectory()) {
				throw new CmdLineException(null, "Directory not found: " + file.getAbsolutePath());
			}
		}
	}

}

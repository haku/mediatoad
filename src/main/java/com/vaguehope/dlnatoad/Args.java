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
	@Option(name = "-i", aliases = { "--interface" }, usage = "Hostname or IP address of interface to bind to.") private String iface;
	@Option(name = "-d", aliases = { "--daemon" }, usage = "detach form terminal and run in bakground.") private boolean daemonise;
	@Option(name = "-p", aliases = { "--preserve" }, usage = "preserve directory hierarchy.") private boolean preserveHierarchy;
	@Option(name = "-a", aliases = { "--accesslog" }, usage = "print access log line at end of each request.") private boolean printAccessLog;
	@Option(name = "-v", aliases = { "--verbose" }, usage = "print log lines for various events.") private boolean verboseLog;
	@Option(name = "--db", usage = "Path for metadata DB.") private String db;
	@Option(name = "--thumbs", usage = "Path for caching image thumbnails.") private String thumbsDir;
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
			dirs.addAll(cliDirs);
		}

		if (dirs.size() < 1) dirs.add(new File("."));

		final List<File> cDirs = new ArrayList<File>();
		for (final File dir : dirs) {
			cDirs.add(dir.getCanonicalFile());
		}

		return cDirs;
	}

	public String getInterface () {
		return this.iface;
	}

	public boolean isDaemonise() {
		return this.daemonise;
	}

	public boolean isPreserveHierarchy () {
		return this.preserveHierarchy;
	}

	public boolean isPrintAccessLog() {
		return this.printAccessLog;
	}

	public boolean isVerboseLog() {
		return this.verboseLog;
	}

	public File getDb () {
		return this.db != null ? new File(this.db) : null;
	}

	public File getThumbsDir() throws CmdLineException {
		if (this.thumbsDir == null) return null;
		final File f = new File(this.thumbsDir);
		if (!f.exists()) throw new CmdLineException(null, "Not found: " + f.getAbsolutePath());
		if (!f.isDirectory()) throw new CmdLineException(null, "Not directory: " + f.getAbsolutePath());
		return f;
	}

	private static List<File> pathsToFiles (final List<String> paths) {
		final List<File> files = new ArrayList<File>();
		for (final String path : paths) {
			files.add(new File(path));
		}
		return files;
	}

	private static void checkDirExist (final List<File> files) throws CmdLineException {
		for (final File file : files) {
			if (!file.exists() || !file.isDirectory()) {
				throw new CmdLineException(null, "Directory not found: " + file.getAbsolutePath());
			}
		}
	}

}

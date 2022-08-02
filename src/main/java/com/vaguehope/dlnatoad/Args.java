package com.vaguehope.dlnatoad;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class Args {

	@Option(name = "-h", aliases = { "--help" }, usage = "Print this help text.") private boolean help;
	@Option(name = "-t", aliases = { "--tree" }, metaVar = "<file>", usage = "File root dirs to scan, one per line.") private String treePath;
	@Option(name = "-p", aliases = { "--port" }, usage = "Local port to bind to.") private int port;
	@Option(name = "-i", aliases = { "--interface" }, usage = "Hostname or IP address of interface to bind to.") private String iface;
	@Option(name = "-d", aliases = { "--daemon" }, usage = "Detach form terminal and run in bakground.") private boolean daemonise;
	@Option(name = "-s", aliases = { "--simplify" }, usage = "Simplify directory structure.") private boolean simplifyHierarchy;
	@Option(name = "-a", aliases = { "--accesslog" }, usage = "Print access log line at end of each request.") private boolean printAccessLog;
	@Option(name = "-v", aliases = { "--verbose" }, usage = "Print log lines for various events.") private boolean verboseLog;
	@Option(name = "--userfile", usage = "Path for to file of users and passwords.") private String userfile;
	@Option(name = "--adduser", usage = "Interactivly add user to userfile.") private boolean addUser;
	@Option(name = "--db", usage = "Path for metadata DB.") private String db;
	@Option(name = "--thumbs", usage = "Path for caching image thumbnails.") private String thumbsDir;
	@Option(name = "--dropdir", usage = "Path for droping metadata import files into.") private String dropDir;
	@Argument(multiValued = true, metaVar = "DIR") private List<String> dirPaths;

	public static class ArgsException extends Exception {
		private static final long serialVersionUID = 4160594293982918286L;
		public ArgsException(String msg) {
			super(msg);
		}
	}

	public boolean isHelp() {
		return this.help;
	}

	public List<File> getDirs () throws ArgsException, IOException {
		final List<File> dirs = new ArrayList<>();

		if (this.treePath != null && this.treePath.length() > 0) {
			final File treeFile = new File(this.treePath);
			if (!treeFile.exists()) throw new ArgsException("File not found: " + this.treePath);
			if (!treeFile.isFile()) throw new ArgsException("Not a file: " + this.treePath);
			for (final String line : FileUtils.readLines(treeFile, Charset.defaultCharset())) {
				if (line.length() < 1 || line.startsWith("#")) continue;
				final File lineDir = new File(line);
				if (!lineDir.exists()) throw new ArgsException("Directory not found: " + line);
				if (!lineDir.isDirectory()) throw new ArgsException("Not a directory: " + line);
				dirs.add(lineDir);
			}
		}

		if (this.dirPaths != null && this.dirPaths.size() > 0) {
			final List<File> cliDirs = pathsToFiles(this.dirPaths);
			checkDirExist(cliDirs);
			dirs.addAll(cliDirs);
		}

		if (dirs.size() < 1) dirs.add(new File("."));

		final List<File> cDirs = new ArrayList<>();
		for (final File dir : dirs) {
			cDirs.add(dir.getCanonicalFile());
		}

		return cDirs;
	}

	public int getPort() {
		return this.port;
	}

	public String getInterface () {
		return this.iface;
	}

	public boolean isDaemonise() {
		return this.daemonise;
	}

	public boolean isSimplifyHierarchy () {
		return this.simplifyHierarchy;
	}

	public boolean isPrintAccessLog() {
		return this.printAccessLog;
	}

	public boolean isVerboseLog() {
		return this.verboseLog;
	}

	public File getUserfile() {
		return this.userfile != null ? new File(this.userfile) : null;
	}

	public boolean isAddUser() {
		return this.addUser;
	}

	public File getDb () {
		return this.db != null ? new File(this.db) : null;
	}

	public File getThumbsDir() throws ArgsException {
		return checkIsDirOrNull(this.thumbsDir);
	}

	public File getDropDir() throws ArgsException {
		if (this.dropDir != null && this.db == null) throw new ArgsException("--dropdir requires --db to be set.");
		return checkIsDirOrNull(this.dropDir);
	}

	private static File checkIsDirOrNull(final String path) throws ArgsException {
		if (path == null) return null;
		final File f = new File(path);
		if (!f.exists()) throw new ArgsException("Not found: " + f.getAbsolutePath());
		if (!f.isDirectory()) throw new ArgsException("Not directory: " + f.getAbsolutePath());
		return f;
	}

	private static List<File> pathsToFiles (final List<String> paths) {
		final List<File> files = new ArrayList<>();
		for (final String path : paths) {
			files.add(new File(path));
		}
		return files;
	}

	private static void checkDirExist (final List<File> files) throws ArgsException {
		for (final File file : files) {
			if (!file.exists() || !file.isDirectory()) {
				throw new ArgsException("Directory not found: " + file.getAbsolutePath());
			}
		}
	}

}

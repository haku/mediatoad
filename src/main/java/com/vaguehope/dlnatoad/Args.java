package com.vaguehope.dlnatoad;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class Args {

	@Option(name = "-h", aliases = { "--help" }, usage = "Print this help text.") private boolean help;
	@Option(name = "-d", aliases = { "--daemon" }, usage = "Detach form terminal and run in bakground.") private boolean daemonise;
	@Option(name = "-v", aliases = { "--verbose" }, usage = "Print log lines for various events.") private boolean verboseLog;

	// MEDIA
	@Option(name = "-t", aliases = { "--tree" }, metaVar = "<file>", usage = "File root dirs to scan, one per line.") private String treePath;
	@Option(name = "-s", aliases = { "--simplify" }, usage = "Simplify directory structure.") private boolean simplifyHierarchy;
	@Option(name = "--db", usage = "Path for metadata DB.") private String db;
	@Option(name = "--thumbs", usage = "Path for caching image thumbnails.") private String thumbsDir;
	@Option(name = "--dropdir", usage = "Path for droping metadata import files into.") private String dropDir;
	@Argument(multiValued = true, metaVar = "DIR") private List<String> dirPaths;

	// HTTP
	@Option(name = "-p", aliases = { "--port" }, usage = "Local port to bind to.") private int port;
	@Option(name = "-i", aliases = { "--interface" }, usage = "Hostname or IP addresses of interfaces to bind to.") private List<String> ifaces;
	@Option(name = "-a", aliases = { "--accesslog" }, usage = "Print access log line at end of each request.") private boolean printAccessLog;

	// DLNA
	@Option(name = "--idfile", usage = "Path for system UUID persistance.") private String idfile;

	// USERS
	@Option(name = "--userfile", usage = "Path for to file of users and passwords.") private String userfile;
	@Option(name = "--sessiondir", usage = "Path for droping metadata import files into.") private String sessionDir;
	@Option(name = "--adduser", usage = "Interactivly add user to userfile.") private boolean addUser;

	@Option(name = "--openid-issuer-uri", usage = "OpenID configurtion issuer URI, /.well-known/openid-configuration will be appended.") private String openIdIssuerUri;
	@Option(name = "--openid-client-id", usage = "OpenID client ID.") private String openIdClientId;
	@Option(name = "--openid-client-secret-file", usage = "Path to file containing secret on the first line.") private String openIdClientSecretFile;
	@Option(name = "--openid-insecure", usage = "Do not mark auth cookie as secure.") private boolean openIdInsecure = false;

	// RPC
	@Option(name = "--rpcauth", usage = "Path for RPC auth file.") private String rpcAuthFile;
	@Option(name = "--remote", usage = "HTTP(S) address of remote instance.", metaVar = "https://example.com/") private List<String> remotes;
	@Option(name = "--tagdeterminer", usage = "HTTP(S) address of remote a TagDeterminer and query for which items it should be offered.", metaVar = "https://example.com/|f~mydir/path") private List<String> tagDeterminers;

	// DEV
	@Option(name = "--webroot", usage = "Override static file location, useful for UI dev.") private String webRoot;
	@Option(name = "--templateroot", usage = "Override mustache template location, useful for UI dev.") private String templateRoot;

	public static class ArgsException extends Exception {
		private static final long serialVersionUID = 4160594293982918286L;
		public ArgsException(final String msg) {
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

	/**
	 * Returns null for no interfaces.
	 * Never returns an empty list.
	 */
	public List<InetAddress> getInterfaces() throws UnknownHostException {
		if (this.ifaces == null || this.ifaces.size() < 1) return null;

		final List<InetAddress> ret = new ArrayList<>();
		for (final String iface : this.ifaces) {
			ret.add(InetAddress.getByName(iface));
		}
		return Collections.unmodifiableList(ret);
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

	public File getIdfile() {
		return this.idfile != null ? new File(this.idfile) : null;
	}

	public File getUserfile() {
		return this.userfile != null ? new File(this.userfile) : null;
	}

	public File getSessionDir() throws ArgsException {
		return checkIsDirOrNull(this.sessionDir);
	}

	public boolean isAddUser() {
		return this.addUser;
	}

	public boolean isOpenIdFlagSet() {
		return this.openIdIssuerUri != null || this.openIdClientId != null || this.openIdClientSecretFile != null;
	}

	public String getOpenIdIssuerUri() throws ArgsException {
		if (this.openIdIssuerUri == null) throw new ArgsException("--openid-issuer-uri not specified.");
		return this.openIdIssuerUri;
	}

	public String getOpenIdClientId() throws ArgsException {
		if (this.openIdClientId == null) throw new ArgsException("--openid-client-id not specified.");
		return this.openIdClientId;
	}

	public File getOpenIdClientSecretFile() throws ArgsException {
		if (this.openIdClientSecretFile == null) throw new ArgsException("--openid-client-secret-file not specified.");
		return checkIsFile(this.openIdClientSecretFile);
	}

	public boolean isOpenIdInsecure() {
		return this.openIdInsecure;
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

	public File getRpcAuthFile() {
		return this.rpcAuthFile != null ? new File(this.rpcAuthFile) : null;
	}

	public List<String> getRemotes() {
		if (this.remotes == null) return Collections.emptyList();
		return this.remotes;
	}

	public List<String> getTagDeterminers() {
		if (this.tagDeterminers == null) return Collections.emptyList();
		return this.tagDeterminers;
	}

	public File getWebRoot() throws ArgsException {
		return checkIsDirOrNull(this.webRoot);
	}

	public File getTemplateRoot() throws ArgsException {
		return checkIsDirOrNull(this.templateRoot);
	}

	private static File checkIsDirOrNull(final String path) throws ArgsException {
		if (path == null) return null;
		final File f = new File(path);
		if (!f.exists()) throw new ArgsException("Not found: " + f.getAbsolutePath());
		if (!f.isDirectory()) throw new ArgsException("Not directory: " + f.getAbsolutePath());
		return f;
	}

	private static File checkIsFile(final String path) throws ArgsException {
		final File f = new File(path);
		if (!f.exists()) throw new ArgsException("Not found: " + f.getAbsolutePath());
		if (!f.isFile()) throw new ArgsException("Not a file: " + f.getAbsolutePath());
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

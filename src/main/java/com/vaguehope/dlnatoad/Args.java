package com.vaguehope.dlnatoad;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

public class Args {

	@Option(name = "-r", aliases = { "--refresh" }) private boolean refresh;

	@Argument(multiValued = true, metaVar = "DIR") private List<String> dirPaths;

	public List<File> getDirs () throws CmdLineException {
		List<File> files;
		if (this.dirPaths == null || this.dirPaths.isEmpty()) {
			files = new ArrayList<File>();
			files.add(new File("."));
		}
		else {
			files = pathsToFiles(this.dirPaths);
			checkDirExist(files);
		}
		return files;
	}

	public boolean isRefresh () {
		return this.refresh;
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

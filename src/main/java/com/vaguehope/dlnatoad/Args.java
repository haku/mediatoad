package com.vaguehope.dlnatoad;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;

public class Args {

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

	private static List<File> pathsToFiles (List<String> paths) {
		List<File> files = new ArrayList<File>();
		for (String path : paths) {
			files.add(new File(path));
		}
		return files;
	}

	private static void checkDirExist (List<File> files) throws CmdLineException {
		for (File file : files) {
			if (!file.exists() || !file.isDirectory()) {
				throw new CmdLineException(null, "Directory not found: " + file.getAbsolutePath());
			}
		}
	}

}

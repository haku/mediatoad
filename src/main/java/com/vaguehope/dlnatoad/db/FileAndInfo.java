package com.vaguehope.dlnatoad.db;

import java.io.File;

class FileAndInfo {

		private final File file;
		private final FileInfo info;

		public FileAndInfo (final File file, final FileInfo info) {
			if (file == null) throw new IllegalArgumentException("File can not be null.");
			if (info == null) throw new IllegalArgumentException("Info can not be null.");
			this.file = file;
			this.info = info;
		}

		public File getFile () {
			return this.file;
		}

		public FileInfo getInfo() {
			return this.info;
		}

	}

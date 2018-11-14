package com.vaguehope.dlnatoad.db;

import java.io.File;

class FileAndDuration {

		private final File file;
		private final long duration;

		public FileAndDuration (final File file, final long duration) {
			if (file == null) throw new IllegalArgumentException("File can not be null.");
			if (duration < 1) throw new IllegalArgumentException("Duration can not be <1.");
			this.file = file;
			this.duration = duration;
		}

		public File getFile () {
			return this.file;
		}

		public long getDuration() {
			return this.duration;
		}

	}

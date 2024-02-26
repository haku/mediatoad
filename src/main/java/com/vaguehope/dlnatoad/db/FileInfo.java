package com.vaguehope.dlnatoad.db;

import com.google.common.base.Objects;

public class FileInfo {

	private final long durationMillis;
	private final int width;
	private final int height;

	public FileInfo(long durationMillis, int width, int height) {
		this.durationMillis = durationMillis;
		this.width = width;
		this.height = height;
	}

	public long getDurationMillis() {
		return this.durationMillis;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public boolean hasDuration() {
		return this.durationMillis > 0;
	}

	public boolean hasWidthAndHeight() {
		return this.width > 0 && this.height > 0;
	}

	@Override
	public boolean equals(final Object aThat) {
		if (aThat == null) return false;
		if (this == aThat) return true;
		if (!(aThat instanceof FileInfo)) return false;
		final FileInfo that = (FileInfo) aThat;

		return Objects.equal(this.durationMillis, that.durationMillis)
				&& Objects.equal(this.width, that.width)
				&& Objects.equal(this.height, that.height);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.durationMillis, this.width, this.height);
	}

}

package com.vaguehope.dlnatoad.db;

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


}

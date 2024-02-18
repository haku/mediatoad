package com.vaguehope.dlnatoad.ffmpeg;

import java.util.Set;

public class FfprobeInfo {

	private final Set<String> codecs;
	private final Set<String> profiles;
	private final long durationMillis;
	private final int width;
	private final int height;

	public FfprobeInfo(final Set<String> codecs, final Set<String> profiles, final long durationMillis, final int width, final int height) {
		this.codecs = codecs;
		this.profiles = profiles;
		this.durationMillis = durationMillis;
		this.width = width;
		this.height = height;
	}

	public Set<String> getCodecs() {
		return this.codecs;
	}

	public Set<String> getProfiles() {
		return this.profiles;
	}

	public boolean has10BitColour() {
		for (final String profile : this.profiles) {
			if (profile.endsWith(" 10")) return true;
		}
		return false;
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

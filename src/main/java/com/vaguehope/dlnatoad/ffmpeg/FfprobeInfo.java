package com.vaguehope.dlnatoad.ffmpeg;

import java.util.Set;

public class FfprobeInfo {

	private final Set<String> codecs;
	private final Set<String> profiles;
	private final Long durationMillis;

	public FfprobeInfo (final Set<String> codecs, final Set<String> profiles, final Long durationMillis) {
		this.codecs = codecs;
		this.profiles = profiles;
		this.durationMillis = durationMillis;
	}

	public Set<String> getCodecs () {
		return this.codecs;
	}

	public Set<String> getProfiles () {
		return this.profiles;
	}

	public boolean has10BitColour () {
		for (final String profile : this.profiles) {
			if (profile.endsWith(" 10")) return true;
		}
		return false;
	}

	public Long getDurationMillis () {
		return this.durationMillis;
	}

}

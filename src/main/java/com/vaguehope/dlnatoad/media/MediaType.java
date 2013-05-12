package com.vaguehope.dlnatoad.media;

public enum MediaType {

	VIDEO("video-"),
	IMAGE("image-"),
	AUDIO("audio-");

	private final String idPrefix;

	private MediaType (final String idPrefix) {
		this.idPrefix = idPrefix;
	}

	public String idPrefix () {
		return this.idPrefix;
	}

}

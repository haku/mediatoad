package com.vaguehope.dlnatoad.dlnaserver;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import com.vaguehope.dlnatoad.util.CollectionHelper;
import com.vaguehope.dlnatoad.util.CollectionHelper.Function;

public enum ContentGroup {

	ROOT("0", "-", "Root"), // Root id of '0' is in the spec.
	RECENT("0-recent", null, "Recent"),
	VIDEO("1-videos", "video-", "Videos"),
	IMAGE("2-images", "image-", "Images"),
	AUDIO("3-audio", "audio-", "Audio"),
	SUBTITLES("4-subtitles", "subtitles-", "Subtitles"),
	THUMBNAIL("5-thumbnails", "thumbnail-", "Thumbnails");

	private static final Collection<String> IDS = Collections.unmodifiableCollection(
			CollectionHelper.map(values(), new Function<ContentGroup, String>() {
				@Override
				public String exec (final ContentGroup input) {
					return input.getId();
				}
			}, new HashSet<String>()));

	private final String id;
	private final String itemIdPrefix;
	private final String humanName;

	private ContentGroup (final String id, final String itemIdPrefix, final String humanName) {
		this.id = id;
		this.itemIdPrefix = itemIdPrefix;
		this.humanName = humanName;
	}

	public String getId () {
		return this.id;
	}

	public String getItemIdPrefix () {
		return this.itemIdPrefix;
	}

	public String getHumanName () {
		return this.humanName;
	}

	public static boolean incluesId (final String id) {
		return IDS.contains(id);
	}

}

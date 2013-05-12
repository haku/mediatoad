package com.vaguehope.dlnatoad.dlnaserver;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import com.vaguehope.dlnatoad.util.CollectionHelper;
import com.vaguehope.dlnatoad.util.CollectionHelper.Function;

public enum ContentGroup {

	ROOT("0", "Root"), // Root id of '0' is in the spec.
	VIDEO("1-videos", "Videos"),
	IMAGE("2-images", "Images"),
	AUDIO("3-audio", "Audio");

	private static final Collection<String> IDS = Collections.unmodifiableCollection(
			CollectionHelper.map(values(), new Function<ContentGroup, String>() {
				@Override
				public String exec (final ContentGroup input) {
					return input.getId();
				}
			}, new HashSet<String>()));

	private final String id;
	private final String humanName;

	private ContentGroup (final String id, final String humanName) {
		this.id = id;
		this.humanName = humanName;
	}

	public String getId () {
		return this.id;
	}

	public String getHumanName () {
		return this.humanName;
	}

	public static boolean incluesId (final String id) {
		return IDS.contains(id);
	}

}

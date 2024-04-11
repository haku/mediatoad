package com.vaguehope.dlnatoad.auth;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

public enum Permission {
	EDITTAGS("+edittags"),
	EDITDIRPREFS("+editdirprefs");

	private static final Map<String, Permission> KEY_TO_PERMISSION;
	static {
		final Builder<String, Permission> builder = ImmutableMap.builder();
		for (final Permission p : Permission.values()) {
			builder.put(p.authListKey, p);
		}
		KEY_TO_PERMISSION = builder.build();
	}

	public static Permission fromKey(String key) {
		return KEY_TO_PERMISSION.get(key);
	}

	private final String authListKey;

	private Permission(final String authListKey) {
		this.authListKey = authListKey;
	}

}

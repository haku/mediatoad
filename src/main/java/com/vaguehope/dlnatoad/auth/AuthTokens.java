package com.vaguehope.dlnatoad.auth;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class AuthTokens {

	private static final Cache<String, String> TOKENS = CacheBuilder.newBuilder()
			.expireAfterAccess(Auth.MAX_TOKEN_AGE_MILLIS, TimeUnit.MILLISECONDS)
			.build();

	public String newToken(final String username) throws IOException {
		final String token = UUID.randomUUID().toString();
		TOKENS.put(token, username);
		return token;
	}

	public String usernameForToken(final String token) {
		return TOKENS.getIfPresent(token);
	}

}

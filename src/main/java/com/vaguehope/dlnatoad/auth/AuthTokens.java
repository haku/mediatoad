package com.vaguehope.dlnatoad.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vaguehope.dlnatoad.C;

public class AuthTokens {

	private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
	private static final Logger LOG = LoggerFactory.getLogger(AuthTokens.class);

	private final File sessionDir;

	public AuthTokens(final File sessionDir) throws IOException {
		this.sessionDir = sessionDir;
		readAllPersistedTokensIntoCache();
	}

	private final Cache<String, String> newTokens = CacheBuilder.newBuilder()
			.expireAfterAccess(Auth.MAX_TOKEN_AGE_MILLIS, TimeUnit.MILLISECONDS)
			.build();

	private final Cache<String, String> persistedTokens = CacheBuilder.newBuilder()
			.expireAfterAccess(Auth.MAX_TOKEN_AGE_MILLIS, TimeUnit.MILLISECONDS)
			.build();

	public String newToken(final String username) throws IOException {
		final String token = UUID.randomUUID().toString();
		this.newTokens.put(token, username);
		return token;
	}

	public String usernameForToken(final String token) throws IOException {
		final String persisted = this.persistedTokens.getIfPresent(token);
		if (persisted != null) return persisted;

		final String username = this.newTokens.getIfPresent(token);
		if (username == null) return null;

		this.persistedTokens.put(token, username);
		persistToken(token, username);
		return username;
	}

	private void persistToken(final String token, final String username) throws IOException {
		if (this.sessionDir == null) return;

		final File file = new File(this.sessionDir, token);
		FileUtils.writeStringToFile(file, username, StandardCharsets.UTF_8);
	}

	private void readAllPersistedTokensIntoCache() throws IOException {
		if (this.sessionDir == null) return;

		final File[] files = this.sessionDir.listFiles();
		if (files == null) throw new IOException("Failed to list files in sessions dir: " + this.sessionDir.getAbsolutePath());

		for (final File file : files) {
			if (!file.isFile()) continue;

			final String token = file.getName();
			if (!UUID_PATTERN.matcher(token).matches()) {
				LOG.warn("Invalid token in session dir: {}", file.getAbsolutePath());
				continue;
			}

			if (System.currentTimeMillis() - file.lastModified() > Auth.MAX_TOKEN_AGE_MILLIS) {
				FileUtils.delete(file);
				continue;
			}

			final String username = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
			if (!C.USERNAME_PATTERN.matcher(username).matches()) {
				LOG.warn("Invalid username in token file: {}", file.getAbsolutePath());
				continue;
			}

			this.persistedTokens.put(token, username);
		}

		LOG.info("Loaded {} sessions from: {}", this.persistedTokens.size(), this.sessionDir.getAbsolutePath());
	}

}

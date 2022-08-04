package com.vaguehope.dlnatoad.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vaguehope.dlnatoad.C;

public class AuthList {

	private static final String AUTH_FILE_NAME = "AUTH";
	private static final AuthList EMPTY_AUTH_LIST = new AuthList(Collections.emptySet());
	private static final Logger LOG = LoggerFactory.getLogger(AuthList.class);

	/**
	 * Returns null if no auth needed.
	 * Returns empty auth list if no users are allowed.
	 */
	public static AuthList forDir(final File dir) throws IOException {
		if (dir == null) throw new IllegalArgumentException("Can not load auth list for null dir.");
		if (!dir.exists() || !dir.isDirectory()) {
			throw new IOException("Can not load AUTH file for non existing directory: " + dir.getAbsolutePath());
		}

		final Set<String> names = getNamesForDir(dir);
		if (names == null) return null;
		if (names.isEmpty()) return EMPTY_AUTH_LIST;
		return new AuthList(names);
	}

	private static final Cache<String, Optional<Set<String>>> AUTH_FILE_CACHE = CacheBuilder.newBuilder()
			.expireAfterWrite(5, TimeUnit.SECONDS)
			.build();

	private static Set<String> getNamesForDir(final File dir) throws IOException {
		try {
			return AUTH_FILE_CACHE.get(dir.getAbsolutePath(), () -> Optional.ofNullable(readNamesForDir(dir))).orElse(null);
		}
		catch (final ExecutionException e) {
			throw new IOException(e);
		}
	}

	private static Set<String> readNamesForDir(final File dir) throws IOException {
		final File parentDir = dir.getAbsoluteFile().getParentFile();
		final Set<String> parentNames = parentDir != null ? getNamesForDir(parentDir) : null;

		final File authFile = new File(dir, AUTH_FILE_NAME);
		if (!authFile.exists()) return parentNames;

		final Set<String> names = readNamesFromAuthFile(authFile);
		if (parentNames != null) names.retainAll(parentNames);
		return names;
	}

	private static Set<String> readNamesFromAuthFile(final File authFile) throws IOException {
		final List<String> lines = FileUtils.readLines(authFile, Charset.forName("UTF-8"));
		Set<String> names = null;
		for (final String line : lines) {
			final String l = line.trim();
			if (l.length() < 1 || l.startsWith("#") || l.startsWith("//")) continue;

			if (!C.USERNAME_PATTERN.matcher(l).find()) {
				LOG.warn("Invalid AUTH file {} contains username that does not match: {}", authFile.getAbsolutePath(), C.USERNAME_PATTERN);
				return Collections.emptySet();
			}

			if (names == null) names = new HashSet<>();
			names.add(l);
		}
		return names != null ? names : Collections.emptySet();
	}

	private final Set<String> usernames;

	/**
	 * usernames can not be null but it can be empty, which means no one is allowed.
	 */
	private AuthList(final Set<String> usernames) {
		if (usernames == null) throw new IllegalStateException("Set usernames can not be null.");
		this.usernames = Collections.unmodifiableSet(usernames);
	}

	public Set<String> usernames() {
		return this.usernames;
	}

	public boolean hasUser(final String username) {
		return this.usernames.contains(username);
	}

	public int size() {
		return this.usernames.size();
	}

}

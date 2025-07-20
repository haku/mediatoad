package com.vaguehope.dlnatoad.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.vaguehope.dlnatoad.auth.AuthList.AccessType;

public class Authoriser {

	private static final String AUTH_FILE_NAME = "AUTH";
	private static final Logger LOG = LoggerFactory.getLogger(Authoriser.class);

	private final DefaultAccess defaultAccess;
	private final AuthList allUsersAuthList;

	public Authoriser(final DefaultAccess defaultAccess, final Users users) {
		this.defaultAccess = defaultAccess;
		this.allUsersAuthList = new AuthList(users.allUsernames(), AccessType.DEFAULT_ALL_USERS);
	}

	/**
	 * Returns null if no auth needed.
	 * Returns empty auth list if no users are allowed.
	 */
	public AuthList forDir(final File dir) throws IOException {
		if (dir == null) throw new IllegalArgumentException("Can not load auth list for null dir.");
		if (!dir.exists() || !dir.isDirectory()) {
			throw new IOException("Can not load AUTH file for non existing directory: " + dir.getAbsolutePath());
		}
		final AuthList list = getAuthListForDir(dir);

		if (list == null) {
			switch (this.defaultAccess) {
			case ALLOW:
				return null;
			case DENY:
				return this.allUsersAuthList;
			default:
				throw new IllegalArgumentException();
			}
		}

		if (list.size() == 0) return AuthList.EMPTY_AUTH_LIST;
		return list;
	}

	private static final Cache<String, Optional<AuthList>> AUTH_FILE_CACHE = CacheBuilder.newBuilder()
			.expireAfterWrite(1, TimeUnit.MINUTES)
			.build();

	private static AuthList getAuthListForDir(final File dir) throws IOException {
		try {
			return AUTH_FILE_CACHE.get(dir.getAbsolutePath(), () -> Optional.ofNullable(readAuthListForDir(dir))).orElse(null);
		}
		catch (final ExecutionException e) {
			throw new IOException(e);
		}
	}

	private static AuthList readAuthListForDir(final File dir) throws IOException {
		final File parentDir = dir.getAbsoluteFile().getParentFile();
		final AuthList parentList = parentDir != null ? getAuthListForDir(parentDir) : null;

		final File authFile = new File(dir, AUTH_FILE_NAME);
		if (!authFile.exists()) return parentList;

		final Map<String, Set<Permission>> usernamesAndPermissions = readNamesAndPermissionsFromAuthFile(authFile);
		final Set<String> names = usernamesAndPermissions.keySet();
		if (parentList != null) names.retainAll(parentList.usernames());
		return new AuthList(usernamesAndPermissions);
	}

	private static Map<String, Set<Permission>> readNamesAndPermissionsFromAuthFile(final File authFile) throws IOException {
		final List<String> lines = FileUtils.readLines(authFile, Charset.forName("UTF-8"));
		Map<String, Set<Permission>> ret = null;
		for (String line : lines) {
			line = line.trim();
			if (line.length() < 1 || line.startsWith("#") || line.startsWith("//")) continue;

			final String[] lineParts = line.split(" ");
			final String name = lineParts[0];

			if (!C.USERNAME_PATTERN.matcher(name).matches()) {
				LOG.warn("Invalid AUTH file {} contains username that does not match {}: {}", authFile.getAbsolutePath(), C.USERNAME_PATTERN, name);
				return Collections.emptyMap();
			}

			Set<Permission> permissions = null;
			if (lineParts.length > 1) {
				for (int i = 1; i < lineParts.length; i++) {
					final String part = lineParts[i];
					final Permission permission = Permission.fromKey(part);
					if (permission != null) {
						if (permissions == null) permissions = EnumSet.noneOf(Permission.class);
						permissions.add(permission);
					}
					else {
						LOG.warn("Invalid AUTH file {} contains invalid permission: {}", authFile.getAbsolutePath(), part);
						return Collections.emptyMap();
					}
				}
			}

			if (ret == null) ret = new HashMap<>();
			ret.put(name, permissions != null ? permissions : Collections.emptySet());
		}
		return ret != null ? ret : Collections.emptyMap();
	}

}

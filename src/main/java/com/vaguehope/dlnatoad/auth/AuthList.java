package com.vaguehope.dlnatoad.auth;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.util.HashHelper;

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
		final AuthList list = getAuthListForDir(dir);
		if (list == null) return null;
		if (list.size() == 0) return EMPTY_AUTH_LIST;
		return list;
	}

	/**
	 * Only for testing.
	 */
	public static AuthList ofNames(final String... names) {
		return new AuthList(new HashSet<>(Arrays.asList(names)));
	}

	/**
	 * Only for testing.
	 */
	public static AuthList ofNameAndPermission(final String name, final Permission permission) {
		return new AuthList(ImmutableMap.of(name, ImmutableSet.of(permission)));
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

	private final BigInteger id;
	private final Map<String, Set<Permission>> usernamesAndPermissions;

	/**
	 * usernames can not be null but it can be empty, which means no one is allowed.
	 */
	private AuthList(final Set<String> usernames) {
		this(usernames.stream().collect(Collectors.toMap(Function.identity(), u -> Collections.emptySet())));
	}

	private AuthList(final Map<String, Set<Permission>> usernamesAndPermissions) {
		if (usernamesAndPermissions == null) throw new IllegalStateException("Usernames can not be null.");
		this.usernamesAndPermissions = Collections.unmodifiableMap(new TreeMap<>(usernamesAndPermissions));  // Sort the list.
		this.id = HashHelper.sha1(StringUtils.join(this.usernamesAndPermissions.keySet(), "\n"));

		// Probably excessive, but better to be sure.
		if (this.id.equals(BigInteger.ZERO)) throw new IllegalStateException("AuthList can not SHA1 to 0.");
	}

	public BigInteger getId() {
		return this.id;
	}

	public Set<String> usernames() {
		return this.usernamesAndPermissions.keySet();
	}

	public boolean hasUser(final String username) {
		if (username == null) return false;
		return this.usernamesAndPermissions.containsKey(username);
	}

	public boolean hasUserWithPermission(final String username, final Permission permission) {
		if (username == null) return false;
		final Set<Permission> permissions = this.usernamesAndPermissions.get(username);
		if (permissions == null) return false;
		return permissions.contains(permission);
	}

	public int size() {
		return this.usernamesAndPermissions.size();
	}

	@Override
	public int hashCode() {
		return this.id.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof AuthList)) return false;
		final AuthList that = (AuthList) obj;
		return Objects.equals(this.id, that.id);
	}

}

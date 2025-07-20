package com.vaguehope.dlnatoad.auth;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.vaguehope.dlnatoad.util.HashHelper;

public class AuthList {

	public static enum AccessType {
		/**
		 * Require auth for HTTP access but not local DLNA access.
		 */
		DEFAULT_DENY,

		/**
		 * Require auth for from both HTTP and DLNA.
		 */
		USER_LIST
	}

	protected static final AuthList DEFAULT_DENY_AUTH_LIST = new AuthList(AccessType.DEFAULT_DENY);
	protected static final AuthList EMPTY_AUTH_LIST = new AuthList(AccessType.USER_LIST);

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

	private final BigInteger id;
	private final Map<String, Set<Permission>> usernamesAndPermissions;
	private final AccessType accessType;

	protected AuthList(AccessType accessType) {
		this(Collections.emptyMap(), accessType);
	}

	protected AuthList(final Map<String, Set<Permission>> usernamesAndPermissions) {
		this(usernamesAndPermissions, AccessType.USER_LIST);
	}

	/**
	 * usernames can not be null but it can be empty, which means no one is allowed.
	 */
	private AuthList(final Set<String> usernames) {
		this(usernames.stream().collect(Collectors.toMap(Function.identity(), u -> Collections.emptySet())));
	}

	private AuthList(final Map<String, Set<Permission>> usernamesAndPermissions, final AccessType accessType) {
		if (usernamesAndPermissions == null) throw new IllegalStateException("Usernames can not be null.");
		this.usernamesAndPermissions = Collections.unmodifiableMap(new TreeMap<>(usernamesAndPermissions));  // Sort the list.
		this.id = HashHelper.sha1(StringUtils.join(this.usernamesAndPermissions.keySet(), "\n"));
		this.accessType = accessType;

		// Probably excessive, but better to be sure.
		if (this.id.equals(BigInteger.ZERO)) throw new IllegalStateException("AuthList can not SHA1 to 0.");
	}

	public BigInteger getId() {
		return this.id;
	}

	public Set<String> usernames() {
		return this.usernamesAndPermissions.keySet();
	}

	public AccessType getAccessType() {
		return this.accessType;
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

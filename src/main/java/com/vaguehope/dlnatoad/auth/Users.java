package com.vaguehope.dlnatoad.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.mindrot.jbcrypt.BCrypt;

public class Users {

	private final Map<String, User> users;

	public Users(final File userfile) throws IOException {
		this.users = Collections.unmodifiableMap(parseUsersFile(userfile));
	}

	public Set<String> allUsernames() {
		return this.users.keySet();
	}

	public User getUser(final String username) {
		return this.users.get(username);
	}

	public User validUser(final String username, final String pass) {
		final User u = this.users.get(username);
		if (u == null) return null;
		if (u.validPass(pass)) return u;
		return null;
	}

	private static Map<String, User> parseUsersFile(final File userfile) throws IOException {
		final Map<String, User> ret = new HashMap<>();

		final List<String> lines = FileUtils.readLines(userfile, Charset.forName("UTF-8"));
		for (String line : lines) {
			line = line.trim();
			if (line.length() < 1 || line.startsWith("#") || line.startsWith("//")) continue;

			final String[] parts = line.split(" ");
			if (parts.length < 1) continue;
			if (parts.length < 2) {
				throw new IOException("Invalid userfile: " + userfile.getAbsolutePath());
			}

			final String username = parts[0];
			final String encPass = parts[1];

			if (encPass.contains(" ")) {
				throw new IOException("Invalid userfile: " + userfile.getAbsolutePath());
			}

			Set<Permission> permissions = null;
			if (parts.length > 2) {
				for (int i = 2; i < parts.length; i++) {
					final String part = parts[i];
					final Permission permission = Permission.fromKey(part);
					if (permission != null) {
						if (permissions == null) permissions = EnumSet.noneOf(Permission.class);
						permissions.add(permission);
					}
					else {
						throw new IOException("Invalid permission in userfile " + userfile.getAbsolutePath() + ": " + part);
					}
				}
			}

			ret.put(username, new User(username, encPass, permissions));
		}
		return ret;
	}

	public static class User {

		private final String username;
		private final String encPass;
		private final Set<Permission> permissions;

		public User(final String username, final String encPass, final Set<Permission> permissions) {
			this.username = username;
			this.encPass = encPass;
			this.permissions = permissions;
		}

		public String getUsername() {
			return this.username;
		}

		public boolean validPass(final String pass) {
			return BCrypt.checkpw(pass, this.encPass);
		}

		public boolean hasPermission(final Permission permission) {
			if (this.permissions == null) return false;
			return this.permissions.contains(permission);
		}

	}

}

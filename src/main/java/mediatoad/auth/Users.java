package mediatoad.auth;

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
import org.apache.commons.lang3.StringUtils;
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
		for (int lineN = 0; lineN < lines.size(); lineN++) {
			final String line = lines.get(lineN).trim();
			if (line.length() < 1 || line.startsWith("#") || line.startsWith("//")) continue;

			final String[] parts = line.split(" ");
			if (parts.length < 1) continue;
			if (parts.length < 2) {
				rejectInvalidFile(userfile, lineN, "not enough parts");
			}

			final String username = parts[0];
			final String encPass = parts[1];

			if (StringUtils.containsWhitespace(encPass))
				rejectInvalidFile(userfile, lineN, "whitespace in encoded password");

			if (!"0".equals(encPass) && encPass.length() < 10)
				rejectInvalidFile(userfile, lineN, "encoded password not long enough");

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
						rejectInvalidFile(userfile, lineN, "invalid permission");
					}
				}
			}

			ret.put(username, new User(username, encPass, permissions));
		}
		return ret;
	}

	private static void rejectInvalidFile(final File userfile, final int lineN, final String reason) throws IOException {
		throw new IOException("Invalid userfile line " + (lineN + 1) + ", " + reason + ": " + userfile.getAbsolutePath());
	}

	public static class User {

		private final String username;
		private final String encPass;
		private final Set<Permission> permissions;

		public User(final String username, final String encPass, final Set<Permission> permissions) {
			this.username = username;
			this.encPass = encPass;
			this.permissions = permissions != null ? Collections.unmodifiableSet(permissions) : null;
		}

		public String getUsername() {
			return this.username;
		}

		public boolean validPass(final String pass) {
			if (this.encPass.length() < 10) return false;
			return BCrypt.checkpw(pass, this.encPass);
		}

		public boolean hasPermission(final Permission permission) {
			if (this.permissions == null) return false;
			return this.permissions.contains(permission);
		}

	}

}

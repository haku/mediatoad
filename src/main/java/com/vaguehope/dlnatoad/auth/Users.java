package com.vaguehope.dlnatoad.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
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

	public boolean validUser(final String user, final String pass) {
		final User u = this.users.get(user);
		if (u == null) return false;
		return u.validPass(pass);
	}

	private static Map<String, User> parseUsersFile(final File userfile) throws IOException {
		final Map<String, User> ret = new HashMap<>();

		final List<String> lines = FileUtils.readLines(userfile, Charset.forName("UTF-8"));
		for (final String line : lines) {
			final String l = line.trim();
			if (l.length() < 1 || l.startsWith("#") || l.startsWith("//")) continue;

			final int x = l.indexOf(" ");
			if (x < 1) continue;
			final String user = l.substring(0, x);
			final String encPass = l.substring(x + 1);

			if (encPass.contains(" ")) {
				throw new IOException("Invalid userfile: " + userfile.getAbsolutePath());
			}

			ret.put(user, new User(encPass));
		}
		return ret;
	}

	private static class User {

		private final String encPass;

		public User(final String encPass) {
			this.encPass = encPass;
		}

		public boolean validPass(final String pass) {
			return BCrypt.checkpw(pass, this.encPass);
		}

	}

}

package com.vaguehope.dlnatoad.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.mindrot.jbcrypt.BCrypt;

import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.C;

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

	public static int interactivlyAddUser(final Args args) throws IOException {
		final File userfile = args.getUserfile();
		if (userfile == null) {
			System.out.println("--userfile is required.");
			return 1;
		}

		final Users users = new Users(userfile);
		System.out.println("Existing users: ");
		System.out.println(users.allUsernames());

		@SuppressWarnings("resource")
		final Scanner scanner = new Scanner(System.in);

		System.out.print("Username: ");
		final String username = scanner.next();
		if (!C.USERNAME_PATTERN.matcher(username).find()) {
			System.out.println("Username must match: " + C.USERNAME_PATTERN.pattern());
			return 1;
		}

		if (users.allUsernames().contains(username)) {
			System.out.println("Username already exists.  Users can be removed by deleting lines from the file.");
			return 1;
		}

		System.out.print("Password (not hidden): ");
		final String password = scanner.next();
		if (password.length() < 1) {
			System.out.println("Password can not be empty.");
			return 1;
		}

		final String encPass = BCrypt.hashpw(password, BCrypt.gensalt());
		final String line = String.format("%s %s\n", username, encPass);
		FileUtils.write(userfile, line, "UTF-8", true);

		return 0;
	}

}

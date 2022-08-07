package com.vaguehope.dlnatoad.auth;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.mindrot.jbcrypt.BCrypt;

import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.C;

public class UsersCli {

	private UsersCli() {
	}

	public static int interactivlyAddUser(final Args args) throws IOException {
		final File userfile = args.getUserfile();
		if (userfile == null) {
			System.out.println("--userfile is required.");
			return 1;
		}

		System.out.print("Existing users: ");
		final Users users;
		if (userfile.exists()) {
			users = new Users(userfile);
			System.out.println();
			System.out.println(users.allUsernames());
		}
		else {
			users = null;
			System.out.println("(file does not exist)");
		}

		@SuppressWarnings("resource")
		final Scanner scanner = new Scanner(System.in);

		System.out.print("Username: ");
		final String username = scanner.next();
		if (!C.USERNAME_PATTERN.matcher(username).find()) {
			System.out.println("Username must match: " + C.USERNAME_PATTERN.pattern());
			return 1;
		}

		if (users != null && users.allUsernames().contains(username)) {
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

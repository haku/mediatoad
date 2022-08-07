package com.vaguehope.dlnatoad.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UsersTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itParsesUsersFile() throws Exception {
		final String data =
				" // a comment\n"
				+ " # another comment\n"
				+ "myuser $2a$10$SjEvTt.bmT24T23CK6cQB.LWvtTWuBeH6W9WixjjEEqcww2/ZnLz.\n"
				+ "anotheruser $2a$10$CXay0mG8MKHCWtwdyX/LmeBaga4M0Jat6EWcXcvWG6a8o6vPurwum\n";

		final File f = this.tmp.newFile();
		FileUtils.write(f, data, "UTF-8");
		final Users undertest = new Users(f);

		assertThat(undertest.allUsernames(), containsInAnyOrder("myuser", "anotheruser"));
		assertFalse(undertest.validUser("myuser", "12"));
		assertTrue(undertest.validUser("myuser", "123"));
		assertTrue(undertest.validUser("anotheruser", "456"));
	}

}

package mediatoad.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import mediatoad.auth.Users.User;

public class UsersTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itParsesUsersFile() throws Exception {
		final String data =
				" // a comment\n"
				+ " # another comment\n"
				+ "   "
				+ "myuser $2a$10$SjEvTt.bmT24T23CK6cQB.LWvtTWuBeH6W9WixjjEEqcww2/ZnLz. +edittags +managerpc\n"
				+ "anotheruser $2a$10$CXay0mG8MKHCWtwdyX/LmeBaga4M0Jat6EWcXcvWG6a8o6vPurwum\n"
				+ "rpcuser@remote 0 +edittags\n";
		final Users undertest = makeUnderTest(data);

		assertThat(undertest.allUsernames(), containsInAnyOrder("myuser", "anotheruser", "rpcuser@remote"));
		assertNull(undertest.validUser("myuser", "12"));

		final User myuser = undertest.validUser("myuser", "123");
		assertNotNull(myuser);
		assertEquals("myuser", myuser.getUsername());
		assertTrue(myuser.hasPermission(Permission.EDITTAGS));

		assertNotNull(undertest.validUser("anotheruser", "456"));

		final User remoteuser = undertest.getUser("rpcuser@remote");
		assertTrue(remoteuser.hasPermission(Permission.EDITTAGS));
		assertFalse(remoteuser.validPass("0"));
	}

	@Test
	public void itChecksForBadUserfile() throws Exception {
		final String[] lines = new String[] {
				"a",
				"b ",
				"c b",
				"d $2a$10$SjEvTt.bmT24T23CK6cQB.LWvtTWuBeH6W9WixjjEEqcww2/ZnLz.",
				"e $2a$10$SjEvTt.bmT24T23CK6cQB.L WvtTWuBeH6W9WixjjEEqcww2/ZnLz.",
				"f $2a$10$SjEvTt.bmT24T23CK6cQB.LWvtTWuBeH6W9WixjjEEqcww2/ZnLz. +whatever",
		};

		for (final String line : lines) {
			try {
				makeUnderTest(line);
				fail("Expected input to be rejected: \"" + line + "\"");
			}
			catch (IOException e) {
				assertThat(e.getMessage(), startsWith("Invalid userfile line 1, "));
			}
		}
	}

	@Test
	public void itHandlesVariousBadAuthInput() throws Exception {
		final Users undertest = makeUnderTest(
				"u $2a$10$SjEvTt.bmT24T23CK6cQB.LWvtTWuBeH6W9WixjjEEqcww2/ZnLz.\n"
				+ "u2 $2a$10$CXay0mG8MKHCWtwdyX/LmeBaga4M0Jat6EWcXcvWG6a8o6vPurwum\n");
		assertNotNull(undertest.validUser("u", "123"));
		assertNotNull(undertest.validUser("u2", "456"));

		final String[][] badPasses = new String[][] {
				new String[] { "u", null },
				new String[] { "u", new String(new byte[] { '1', '2', '3', 0x00 }) },
				new String[] { "u", new String(new byte[] { '1', '2', 0x00, '3' }) },
				new String[] { "u", new String(new byte[] { 0x00, '1', '2', '3' }) },
				new String[] { new String(new byte[] { 'u', 0x00, '2' }), "123" },
				new String[] { new String(new byte[] { 'u', 0x00, '2' }), "456" },
		};
		for (final String[] creds : badPasses) {
			assertNull(undertest.validUser(creds[0], creds[1]));
		}
	}

	private Users makeUnderTest(final String data) throws IOException {
		final File f = this.tmp.newFile();
		FileUtils.write(f, data, "UTF-8");
		return new Users(f);
	}

}

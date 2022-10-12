package com.vaguehope.dlnatoad.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AuthTokensTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itWorksWithoutDir() throws Exception {
		final AuthTokens undertest = new AuthTokens(null);

		final String token = undertest.newToken("foo");
		assertThat(token, hasLength(36));

		assertEquals("foo", undertest.usernameForToken(token));
	}

	@Test
	public void itWorksWithDir() throws Exception {
		final File dir = this.tmp.newFolder();
		final AuthTokens undertest = new AuthTokens(dir);

		final String token = undertest.newToken("foo");
		assertThat(token, hasLength(36));

		final File tokenFile = new File(dir, token);
		assertFalse(tokenFile.exists());

		assertEquals("foo", undertest.usernameForToken(token));
		assertTrue(tokenFile.exists());
		assertEquals("foo", FileUtils.readFileToString(tokenFile, StandardCharsets.UTF_8));

		final File invalidFile = new File(dir, "invalid-file-name");
		FileUtils.writeStringToFile(invalidFile, "desu", StandardCharsets.UTF_8);
		final String uuid = UUID.randomUUID().toString();
		FileUtils.writeStringToFile(new File(dir, uuid), "invlid|user>name", StandardCharsets.UTF_8);
		FileUtils.forceMkdir(new File(dir, "some-dir"));

		final AuthTokens afterRestart = new AuthTokens(dir);
		assertEquals("foo", afterRestart.usernameForToken(token));
		assertNull(afterRestart.usernameForToken("invalid-file-name"));
		assertNull(afterRestart.usernameForToken(uuid));
	}

	@Test
	public void itExipresTokens() throws Exception {
		final File dir = this.tmp.newFolder();
		final AuthTokens undertest = new AuthTokens(dir);
		final String token = undertest.newToken("foo");
		assertEquals("foo", undertest.usernameForToken(token));

		final AuthTokens afterRestart1 = new AuthTokens(dir);
		assertEquals("foo", afterRestart1.usernameForToken(token));

		final File tokenFile = new File(dir, token);
		assertTrue(tokenFile.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31)));

		final AuthTokens afterRestart2 = new AuthTokens(dir);
		assertNull(afterRestart2.usernameForToken(token));
	}

}

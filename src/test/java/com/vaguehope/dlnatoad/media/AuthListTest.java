package com.vaguehope.dlnatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AuthListTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itReturnsNullForNoAuthFile() throws Exception {
		final File dir = this.tmp.newFolder();
		final AuthList actual = AuthList.forDir(dir);
		assertEquals(null, actual);
	}

	@Test
	public void itReturnsEmptyListForEmptyAuthFile() throws Exception {
		final AuthList actual = writeListAndReadDir("");
		assertEquals(0, actual.size());
	}

	@Test
	public void itReturnsEmptyListForInvalidAuthFile() throws Exception {
		assertEquals(0, writeListAndReadDir("foo bar").size());
		assertEquals(0, writeListAndReadDir("foobar(").size());
		assertEquals(0, writeListAndReadDir("valid\nfoobar(").size());
	}

	@Test
	public void itLoadsSingleLineAuthListWithNoTrailingNewLine() throws Exception {
		assertThat(writeListAndReadDir("foo").usernames(), contains("foo"));
	}

	@Test
	public void itLoadsValidAuthList() throws Exception {
		final String list = "//some comment\n"
				+ "ausername\n"
				+ "\n"
				+ "#some other comment\n"
				+ "anothername\n";
		final AuthList actual = writeListAndReadDir(list);
		assertThat(actual.usernames(), containsInAnyOrder("ausername", "anothername"));
	}

	@Test
	public void itReturnsAuthFileForParentDir() throws Exception {
		final File dir0 = this.tmp.newFolder();
		final File dir1 = mkDir(dir0, "dir1");
		final File dir2 = mkDir(dir1, "dir2");

		writeListAndReadDir(dir0, "usera\nuser1");
		final AuthList actual = AuthList.forDir(dir2);
		assertThat(actual.usernames(), containsInAnyOrder("usera", "user1"));
	}

	@Test
	public void itUsesTheIntersectionOfAuthFilesInParentDirs() throws Exception {
		final File dir0 = this.tmp.newFolder();
		final File dir1 = mkDir(dir0, "dir1");
		final File dir2 = mkDir(dir1, "dir2");

		writeListAndReadDir(dir0, "usera\nuser1");
		writeListAndReadDir(dir1, "usera\nuserb\nuser1\nuser2");
		final AuthList actual = writeListAndReadDir(dir2, "usera\nuserb\nuserc\nuser1\nuser2\nuser3");
		assertThat(actual.usernames(), containsInAnyOrder("usera", "user1"));
	}

	private AuthList writeListAndReadDir(final String list) throws IOException {
		return writeListAndReadDir(this.tmp.newFolder(), list);
	}

	private static AuthList writeListAndReadDir(final File dir, final String list) throws IOException {
		final File file = new File(dir, "AUTH");
		FileUtils.writeStringToFile(file, list, "UTF-8");
		return AuthList.forDir(dir);
	}

	private static File mkDir(final File parentDir, String name) throws IOException {
		File d = new File(parentDir, name);
		FileUtils.forceMkdir(d);
		return d;
	}

}

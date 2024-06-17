package com.vaguehope.dlnatoad.util;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

public class FileHelperTest {

	@Test
	public void itRelativisesPathsWithRoot() throws Exception {
		assertEquals("thing", runRelativePath("/path/to/thing", "/path/to/thing"));
		assertEquals("thing", runRelativePath("/path/to/thing/", "/path/to/thing"));
		assertEquals("thing", runRelativePath("/path/to/thing", "/path/to/thing/"));

		assertEquals("thing/foo", runRelativePath("/path/to/thing", "/path/to/thing/foo"));
		assertEquals("thing/foo/bar", runRelativePath("/path/to/thing", "/path/to/thing/foo/bar"));
		assertEquals("thing/foo/bar", runRelativePath("/path/to/thing/", "/path/to/thing/foo/bar"));

		assertEquals("thing/foo", runRelativePath("C:\\path\\to\\thing", "C:\\path\\to\\thing\\foo"));
		assertEquals("thing/foo/bar", runRelativePath("C:\\path\\to\\thing", "C:\\path\\to\\thing\\foo\\bar"));
		assertEquals("thing/foo/bar", runRelativePath("C:\\path\\to\\thing\\", "C:\\path\\to\\thing\\foo\\bar"));

		assertEquals("thing/foo", runRelativePath("\\\\server\\path\\to\\thing", "\\\\server\\path\\to\\thing\\foo"));
		assertEquals("thing/foo/bar", runRelativePath("\\\\server\\path\\to\\thing", "\\\\server\\path\\to\\thing\\foo\\bar"));
		assertEquals("thing/foo/bar", runRelativePath("\\\\server\\path\\to\\thing\\", "\\\\server\\path\\to\\thing\\foo\\bar"));
	}

	private static String runRelativePath(String root, String sub) {
		return FileHelper.rootAndPath(new File(root), new File(sub));
	}

}

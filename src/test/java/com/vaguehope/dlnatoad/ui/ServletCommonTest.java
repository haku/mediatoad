package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServletCommonTest {

	@Test
	public void itExtractsIdFromPath() throws Exception {
		assertEquals("0", ServletCommon.idFromPath("", "0"));
		assertEquals("0", ServletCommon.idFromPath("/", "0"));
		assertEquals("0", ServletCommon.idFromPath("/dlnatoad", "0"));
		assertEquals("0", ServletCommon.idFromPath("/dlnatoad/", "0"));

		assertEquals("123", ServletCommon.idFromPath("/c/123", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/c/123", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/c/123.ext", "0"));

		assertEquals("123", ServletCommon.idFromPath("/t/123", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/t/123", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/t/123.ext", "0"));

		// Not sure it should do this but it does.
		assertEquals("123", ServletCommon.idFromPath("/foo/123", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/foo/123", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/foo/123.ext", "0"));

		assertEquals("123", ServletCommon.idFromPath("/mediatoad/c/123", "0"));
		assertEquals("123", ServletCommon.idFromPath("/mediatoad/c/123.ext", "0"));
		assertEquals("123", ServletCommon.idFromPath("/mediatoad/t/123", "0"));
		assertEquals("123", ServletCommon.idFromPath("/mediatoad/t/123.ext", "0"));
	}

	@Test
	public void itExtractsFirstDirFromPath() throws Exception {
		assertEquals(null, ServletCommon.firstDirFromPath(null));
		assertEquals(null, ServletCommon.firstDirFromPath(""));
		assertEquals(null, ServletCommon.firstDirFromPath("0"));
		assertEquals(null, ServletCommon.firstDirFromPath("/"));
		assertEquals(null, ServletCommon.firstDirFromPath("/0"));

		assertEquals("0", ServletCommon.firstDirFromPath("/0/"));
		assertEquals("0", ServletCommon.firstDirFromPath("/0/foo.ext"));
		assertEquals("abc", ServletCommon.firstDirFromPath("/abc/foo.ext"));

		assertEquals(null, ServletCommon.firstDirFromPath("/dlnatoad/0"));
		assertEquals("0", ServletCommon.firstDirFromPath("/dlnatoad/0/"));
		assertEquals("0", ServletCommon.firstDirFromPath("/dlnatoad/0/foo.ext"));
		assertEquals("abc", ServletCommon.firstDirFromPath("/dlnatoad/abc/foo.ext"));

		assertEquals(null, ServletCommon.firstDirFromPath("/mediatoad/0"));
		assertEquals("0", ServletCommon.firstDirFromPath("/mediatoad/0/"));
		assertEquals("0", ServletCommon.firstDirFromPath("/mediatoad/0/foo.ext"));
		assertEquals("abc", ServletCommon.firstDirFromPath("/mediatoad/abc/foo.ext"));
	}

	@Test
	public void itExtractsFileFromPath() throws Exception {
		assertEquals(null, ServletCommon.fileFromPath(null));
		assertEquals(null, ServletCommon.fileFromPath(""));
		assertEquals(null, ServletCommon.fileFromPath("0"));
		assertEquals(null, ServletCommon.fileFromPath("/"));

		assertEquals("0", ServletCommon.fileFromPath("/0"));
		assertEquals("0", ServletCommon.fileFromPath("/dlnatoad/0"));
		assertEquals("t=tag", ServletCommon.fileFromPath("/t=tag"));
		assertEquals("t=tag", ServletCommon.fileFromPath("/dlnatoad/t=tag"));

		assertEquals("0", ServletCommon.fileFromPath("/mediatoad/0"));
		assertEquals("t=tag", ServletCommon.fileFromPath("/mediatoad/t=tag"));
	}

}

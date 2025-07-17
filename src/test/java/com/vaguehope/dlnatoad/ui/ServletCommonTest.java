package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.vaguehope.dlnatoad.FakeServletCommon;

public class ServletCommonTest {

	@Test
	public void itExtractsIdFromPath() throws Exception {
		ServletCommon sc = FakeServletCommon.makeWithPathPrefix("dlnatoad");

		assertEquals("0", sc.idFromPath("", "0"));
		assertEquals("0", sc.idFromPath("/", "0"));
		assertEquals("0", sc.idFromPath("/dlnatoad", "0"));
		assertEquals("0", sc.idFromPath("/dlnatoad/", "0"));

		assertEquals("123", sc.idFromPath("/c/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/c/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/c/123.ext", "0"));

		assertEquals("123", sc.idFromPath("/t/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/t/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/t/123.ext", "0"));

		// Not sure it should do this but it does.
		assertEquals("123", sc.idFromPath("/foo/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/foo/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/foo/123.ext", "0"));

		sc = FakeServletCommon.makeWithPathPrefix("mediatoad");

		assertEquals("123", sc.idFromPath("/mediatoad/c/123", "0"));
		assertEquals("123", sc.idFromPath("/mediatoad/c/123.ext", "0"));
		assertEquals("123", sc.idFromPath("/mediatoad/t/123", "0"));
		assertEquals("123", sc.idFromPath("/mediatoad/t/123.ext", "0"));
	}

	@Test
	public void itExtractsFirstDirFromPath() throws Exception {
		ServletCommon sc = FakeServletCommon.makeWithPathPrefix("dlnatoad");

		assertEquals(null, sc.firstDirFromPath(null));
		assertEquals(null, sc.firstDirFromPath(""));
		assertEquals(null, sc.firstDirFromPath("0"));
		assertEquals(null, sc.firstDirFromPath("/"));
		assertEquals(null, sc.firstDirFromPath("/0"));

		assertEquals("0", sc.firstDirFromPath("/0/"));
		assertEquals("0", sc.firstDirFromPath("/0/foo.ext"));
		assertEquals("abc", sc.firstDirFromPath("/abc/foo.ext"));

		assertEquals(null, sc.firstDirFromPath("/dlnatoad/0"));
		assertEquals("0", sc.firstDirFromPath("/dlnatoad/0/"));
		assertEquals("0", sc.firstDirFromPath("/dlnatoad/0/foo.ext"));
		assertEquals("abc", sc.firstDirFromPath("/dlnatoad/abc/foo.ext"));

		sc = FakeServletCommon.makeWithPathPrefix("mediatoad");

		assertEquals(null, sc.firstDirFromPath("/mediatoad/0"));
		assertEquals("0", sc.firstDirFromPath("/mediatoad/0/"));
		assertEquals("0", sc.firstDirFromPath("/mediatoad/0/foo.ext"));
		assertEquals("abc", sc.firstDirFromPath("/mediatoad/abc/foo.ext"));
	}

	@Test
	public void itExtractsFileFromPath() throws Exception {
		ServletCommon sc = FakeServletCommon.makeWithPathPrefix("dlnatoad");

		assertEquals(null, sc.fileFromPath(null));
		assertEquals(null, sc.fileFromPath(""));
		assertEquals(null, sc.fileFromPath("0"));
		assertEquals(null, sc.fileFromPath("/"));

		assertEquals("0", sc.fileFromPath("/0"));
		assertEquals("0", sc.fileFromPath("/dlnatoad/0"));
		assertEquals("t=tag", sc.fileFromPath("/t=tag"));
		assertEquals("t=tag", sc.fileFromPath("/dlnatoad/t=tag"));

		sc = FakeServletCommon.makeWithPathPrefix("mediatoad");

		assertEquals("0", sc.fileFromPath("/mediatoad/0"));
		assertEquals("t=tag", sc.fileFromPath("/mediatoad/t=tag"));
	}

}

package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.vaguehope.dlnatoad.ui.RequestPaths.SearchPath;

public class RequestPathsTest {

	@Test
	public void itParsesSearchPaths() throws Exception {
		assertEquals(new SearchPath("", ""), RequestPaths.parseSearchPath(null));
		assertEquals(new SearchPath("", ""), RequestPaths.parseSearchPath(""));

		assertEquals(new SearchPath("t=foo", ""), RequestPaths.parseSearchPath("/t=foo"));
		assertEquals(new SearchPath("t=foo", ""), RequestPaths.parseSearchPath("/t=foo/"));
		assertEquals(new SearchPath("t=foo", "file.ext"), RequestPaths.parseSearchPath("/t=foo/file.ext"));

		assertEquals(new SearchPath("t=foo", ""), RequestPaths.parseSearchPath("/search/t=foo"));
		assertEquals(new SearchPath("t=foo", ""), RequestPaths.parseSearchPath("/search/t=foo/"));
		assertEquals(new SearchPath("t=foo", "file.ext"), RequestPaths.parseSearchPath("/search/t=foo/file.ext"));
	}

}

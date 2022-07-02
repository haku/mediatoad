package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServletCommonTest {

	@Test
	public void itExtractsIdFromPath() throws Exception {
		assertEquals("0", ServletCommon.idFromPath("", "c/", "0"));
		assertEquals("0", ServletCommon.idFromPath("/", "c/", "0"));
		assertEquals("0", ServletCommon.idFromPath("/dlnatoad", "c/", "0"));
		assertEquals("0", ServletCommon.idFromPath("/dlnatoad/", "c/", "0"));

		assertEquals("123", ServletCommon.idFromPath("/c/123", "c/", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/c/123", "c/", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/c/123.ext", "c/", "0"));

		// Not sure it should do this but it does.
		assertEquals("123", ServletCommon.idFromPath("/foo/123", "c/", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/foo/123", "c/", "0"));
		assertEquals("123", ServletCommon.idFromPath("/dlnatoad/foo/123.ext", "c/", "0"));
	}

}

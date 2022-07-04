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
	}

}

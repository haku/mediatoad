package com.vaguehope.dlnatoad.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class StringHelperTest {

	@Test
	public void itUnquotesStrings () throws Exception {
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("\"foo\\\"bar\""));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("foo\\\"bar\""));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("\"foo\\\"bar"));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("foo\\\"bar"));
	}

	@Test
	public void itRemovesLeadingString() throws Exception {
		assertEquals("foobar", StringHelper.removePrefix("/foobar", "/"));
		assertEquals("", StringHelper.removePrefix("/", "/"));
		assertEquals("foobar", StringHelper.removePrefix("c/foobar", "c/"));

		assertEquals("", StringHelper.removePrefix("", "/"));
		assertEquals(null, StringHelper.removePrefix(null, "/"));
	}

}

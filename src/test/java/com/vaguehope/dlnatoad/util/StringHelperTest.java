package com.vaguehope.dlnatoad.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringHelperTest {

	@Test
	public void itUnquotesStrings () throws Exception {
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("\"foo\\\"bar\""));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("foo\\\"bar\""));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("\"foo\\\"bar"));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("foo\\\"bar"));
	}

}

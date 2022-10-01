package com.vaguehope.dlnatoad.db.search;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.google.common.net.UrlEscapers;

public class DbSearchSyntaxTest {

	@Test
	public void itEscapesQuotes() throws Exception {
		assertEquals("t=a", DbSearchSyntax.makeSingleTagSearch("a"));
		assertEquals("t=\"a b\"", DbSearchSyntax.makeSingleTagSearch("a b"));
		assertEquals("t=\"a\tb\"", DbSearchSyntax.makeSingleTagSearch("a\tb"));
		assertEquals("t=\"a　b\"", DbSearchSyntax.makeSingleTagSearch("a　b"));
		assertEquals("t='a \"b c\"'", DbSearchSyntax.makeSingleTagSearch("a \"b c\""));
		assertEquals("t='a \"b c\" \\'d e\\''", DbSearchSyntax.makeSingleTagSearch("a \"b c\" 'd e'"));
		assertEquals("t=\"foo(b\"", DbSearchSyntax.makeSingleTagSearch("foo(b"));
		assertEquals("t=\"bar)b\"", DbSearchSyntax.makeSingleTagSearch("bar)b"));
	}

	@Test
	public void itUrlEscapesAsExpected() throws Exception {
		assertEquals("t%3D%22foo+bar%22", UrlEscapers.urlFormParameterEscaper().escape("t=\"foo bar\""));
		assertEquals("t%3D6%2Bthings", UrlEscapers.urlFormParameterEscaper().escape("t=6+things"));
	}

}

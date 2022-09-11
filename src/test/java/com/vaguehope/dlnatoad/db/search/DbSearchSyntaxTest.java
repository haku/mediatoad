package com.vaguehope.dlnatoad.db.search;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.google.common.net.UrlEscapers;

public class DbSearchSyntaxTest {

	@Test
	public void itEscapesQuotes() throws Exception {
		assertEquals("t=a", DbSearchSyntax.makeSingleTagSearch("a"));
		assertEquals("t=\"a b\"", DbSearchSyntax.makeSingleTagSearch("a b"));
		assertEquals("t='a \"b c\"'", DbSearchSyntax.makeSingleTagSearch("a \"b c\""));
		assertEquals("t='a \"b c\" \\'d e\\''", DbSearchSyntax.makeSingleTagSearch("a \"b c\" 'd e'"));
	}

	@Test
	public void itDoesSomething() throws Exception {
		assertEquals("t=%22foo%20bar%22", UrlEscapers.urlPathSegmentEscaper().escape("t=\"foo bar\""));
	}

}

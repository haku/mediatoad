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

	@Test
	public void itDeterminsWidthOrHeight() throws Exception {
		assertEquals(null, DbSearchSyntax.widthOrHeight(""));
		assertEquals(null, DbSearchSyntax.widthOrHeight("a"));
		assertEquals(null, DbSearchSyntax.widthOrHeight("<"));
		assertEquals(null, DbSearchSyntax.widthOrHeight("x="));
		assertEquals(null, DbSearchSyntax.widthOrHeight("=1234"));

		assertEquals("width=", DbSearchSyntax.widthOrHeight("w="));
		assertEquals("width<", DbSearchSyntax.widthOrHeight("w<"));
		assertEquals("width<=", DbSearchSyntax.widthOrHeight("w<="));
		assertEquals("width>", DbSearchSyntax.widthOrHeight("w>"));
		assertEquals("width>=", DbSearchSyntax.widthOrHeight("w>="));

		assertEquals("height=", DbSearchSyntax.widthOrHeight("h="));
		assertEquals("height<", DbSearchSyntax.widthOrHeight("h<"));
		assertEquals("height<=", DbSearchSyntax.widthOrHeight("h<="));
		assertEquals("height>", DbSearchSyntax.widthOrHeight("h>"));
		assertEquals("height>=", DbSearchSyntax.widthOrHeight("h>="));
	}

	@Test
	public void itExtractsCounts() throws Exception {
		assertEquals(0, DbSearchSyntax.removeCountOperator("t<0"));
		assertEquals(0, DbSearchSyntax.removeCountOperator("T<0"));

		assertEquals(1, DbSearchSyntax.removeCountOperator("t<1"));
		assertEquals(1, DbSearchSyntax.removeCountOperator("T<1"));

		assertEquals(3, DbSearchSyntax.removeCountOperator("t<3"));
		assertEquals(3, DbSearchSyntax.removeCountOperator("T<3"));

		assertEquals(1, DbSearchSyntax.removeCountOperator("t<"));
		assertEquals(1, DbSearchSyntax.removeCountOperator("T<"));

		assertEquals(1, DbSearchSyntax.removeCountOperator("t<a"));

		assertEquals(0, DbSearchSyntax.removeCountOperator("t>0"));
		assertEquals(0, DbSearchSyntax.removeCountOperator("T>0"));

		assertEquals(1, DbSearchSyntax.removeCountOperator("t>1"));
		assertEquals(1, DbSearchSyntax.removeCountOperator("T>1"));

		assertEquals(2, DbSearchSyntax.removeCountOperator("t>2"));
		assertEquals(2, DbSearchSyntax.removeCountOperator("T>2"));

		assertEquals(1920, DbSearchSyntax.removeCountOperator("w=1920"));
		assertEquals(1920, DbSearchSyntax.removeCountOperator("W=1920"));

		assertEquals(1920, DbSearchSyntax.removeCountOperator("w>1920"));
		assertEquals(1920, DbSearchSyntax.removeCountOperator("W>1920"));

		assertEquals(1920, DbSearchSyntax.removeCountOperator("w>=1920"));
		assertEquals(1920, DbSearchSyntax.removeCountOperator("W>=1920"));

		assertEquals(1920, DbSearchSyntax.removeCountOperator("w<1920"));
		assertEquals(1920, DbSearchSyntax.removeCountOperator("W<1920"));

		assertEquals(1920, DbSearchSyntax.removeCountOperator("w<=1920"));
		assertEquals(1920, DbSearchSyntax.removeCountOperator("W<=1920"));
	}

}

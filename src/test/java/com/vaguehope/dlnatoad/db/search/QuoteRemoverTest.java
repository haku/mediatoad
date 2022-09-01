package com.vaguehope.dlnatoad.db.search;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class QuoteRemoverTest {

	@Test
	public void itUnquotesNull() throws Exception {
		testUnquote(null, null);
	}

	@Test
	public void itUnquotesEmpty() throws Exception {
		testUnquote("", "");
	}

	@Test
	public void itUnquotesNothing() throws Exception {
		testUnquote("abcdef", "abcdef");
	}

	@Test
	public void itUnquotes0() throws Exception {
		testUnquote("\"some 'awesome\\\" band' desu\"", "some 'awesome\" band' desu");
	}

	@Test
	public void itUnquotes1() throws Exception {
		testUnquote("'some \"awesome\\' band\" desu'", "some \"awesome' band\" desu");
	}

	@Test
	public void itUnquotes2() throws Exception {
		testUnquote("some\" \\'media\\\" tag \"", "some \\'media\" tag ");
	}

	@Test
	public void itUnquotes3() throws Exception {
		testUnquote("some' \\\"media\\' tag '", "some \\\"media' tag ");
	}

	@Test
	public void itUnquotes4() throws Exception {
		testUnquote("'some awesome\" band desu'", "some awesome\" band desu");
	}

	@Test
	public void itUnquotes5() throws Exception {
		testUnquote("\"some awesome' band desu\"", "some awesome' band desu");
	}

	@Test
	public void itUnquotes6() throws Exception {
		testUnquote("some' media\\' tag '", "some media' tag ");
	}

	@Test
	public void itUnquotes7() throws Exception {
		testUnquote("'some awesome\" band desu'", "some awesome\" band desu");
	}

	@Test
	public void itUnquotes8() throws Exception {
		testUnquote("\"some awesome' band desu\"", "some awesome' band desu");
	}

	@Test
	public void itUnquotes9() throws Exception {
		testUnquote("some\" media\\\" tag \"", "some media\" tag ");
	}

	@Test
	public void itDoesNotUnquote0() throws Exception {
		testNoUnquote("awesome'\"*%_\\)(band");
	}

	@Test
	public void itDoesNotUnquote1() throws Exception {
		testNoUnquote("t=\"a very long tag that does not fit on the screen properl");
	}

	private static void testNoUnquote(String string) {
		testUnquote(string, string);
	}

	private static void testUnquote(String input, String expected) {
		String actual = QuoteRemover.unquote(input);
		assertEquals("\nI: >" + input + "<\nE: >" + expected + "<\nA: >" + actual + "<", expected, actual);
	}

}

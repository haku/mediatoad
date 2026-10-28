package mediatoad.util;

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

	@Test
	public void itRemovesLeadingString() throws Exception {
		assertEquals("foobar", StringHelper.removePrefix("/foobar", "/"));
		assertEquals("", StringHelper.removePrefix("/", "/"));
		assertEquals("foobar", StringHelper.removePrefix("c/foobar", "c/"));
		assertEquals("/bar", StringHelper.removePrefix("/foo/bar", "/foo"));

		assertEquals("", StringHelper.removePrefix("", "/"));
		assertEquals(null, StringHelper.removePrefix(null, "/"));
	}

	@Test
	public void itRemovesFirstMatchingPrefix() throws Exception {
		assertEquals("foobar", StringHelper.removeFirstMatchingPrefix("/foobar", "/"));
		assertEquals("", StringHelper.removeFirstMatchingPrefix("/", "/"));
		assertEquals("foobar", StringHelper.removeFirstMatchingPrefix("c/foobar", "c/"));
		assertEquals("/bar", StringHelper.removeFirstMatchingPrefix("/foo/bar", "/foo"));

		assertEquals("", StringHelper.removeFirstMatchingPrefix("", "/"));
		assertEquals(null, StringHelper.removeFirstMatchingPrefix(null, "/"));

		assertEquals("/bar", StringHelper.removeFirstMatchingPrefix("/foo/bar", "/foo", "/bat"));
		assertEquals("/bar", StringHelper.removeFirstMatchingPrefix("/bat/bar", "/foo", "/bat"));
	}

	@Test
	public void itRemovesTrailingString() throws Exception {
		assertEquals("foobar", StringHelper.removeSuffix("foobar/", "/"));
		assertEquals("", StringHelper.removeSuffix("/", "/"));
		assertEquals("foobar", StringHelper.removeSuffix("foobar/c", "/c"));

		assertEquals("", StringHelper.removeSuffix("", "/"));
		assertEquals(null, StringHelper.removeSuffix(null, "/"));
	}

	@Test
	public void itExtractsFirstLineWithMaxLength() throws Exception {
		assertEquals("abc", StringHelper.firstLineMaxLength("abc", 5));
		assertEquals("abc", StringHelper.firstLineMaxLength("abc", 3));
		assertEquals("ab[+1]", StringHelper.firstLineMaxLength("abc", 2));

		assertEquals("1[+4]", StringHelper.firstLineMaxLength("1\n2\n3", 1));
		assertEquals("GET[+15]", StringHelper.firstLineMaxLength("GET\nsomething\relse", 20));
		assertEquals("GET[+15]", StringHelper.firstLineMaxLength("GET\rsomething\nelse", 20));
		assertEquals("[+6]", StringHelper.firstLineMaxLength("\nGET\ra", 20));
		assertEquals("[+6]", StringHelper.firstLineMaxLength("\rGET\na", 20));
		assertEquals("some very [+21]", StringHelper.firstLineMaxLength("some very long \n text is \n here", 10));

		assertEquals("", StringHelper.firstLineMaxLength("", 10));
		assertEquals(null, StringHelper.firstLineMaxLength(null, 10));
	}

}

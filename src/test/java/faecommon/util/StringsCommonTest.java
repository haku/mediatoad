package faecommon.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import faecommon.util.StringsCommon;

public class StringsCommonTest {

	@Test
	public void itExtractsFirstLineWithMaxLength() throws Exception {
		assertEquals("abc", StringsCommon.firstLineMaxLength("abc", 5));
		assertEquals("abc", StringsCommon.firstLineMaxLength("abc", 3));
		assertEquals("ab[+1]", StringsCommon.firstLineMaxLength("abc", 2));

		assertEquals("1[+4]", StringsCommon.firstLineMaxLength("1\n2\n3", 1));
		assertEquals("GET[+15]", StringsCommon.firstLineMaxLength("GET\nsomething\relse", 20));
		assertEquals("GET[+15]", StringsCommon.firstLineMaxLength("GET\rsomething\nelse", 20));
		assertEquals("[+6]", StringsCommon.firstLineMaxLength("\nGET\ra", 20));
		assertEquals("[+6]", StringsCommon.firstLineMaxLength("\rGET\na", 20));
		assertEquals("some very [+21]", StringsCommon.firstLineMaxLength("some very long \n text is \n here", 10));

		assertEquals("", StringsCommon.firstLineMaxLength("", 10));
		assertEquals(null, StringsCommon.firstLineMaxLength(null, 10));
	}

	@Test
	public void itEscapesHtml() throws Exception {
		assertEquals("&lt;a&gt;&lt;span&gt;&quot;&lt;script&gt;", StringsCommon.escapeHtml("<a><span>\"<script>"));
	}

}

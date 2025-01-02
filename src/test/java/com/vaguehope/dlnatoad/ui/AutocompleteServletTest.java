package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;

import com.vaguehope.dlnatoad.MockHttpServletRequest;
import com.vaguehope.dlnatoad.MockHttpServletResponse;
import com.vaguehope.dlnatoad.db.TagAutocompleter;
import com.vaguehope.dlnatoad.db.TagFrequency;

public class AutocompleteServletTest {

	private AutocompleteServlet undertest;
	private TagAutocompleter tagAutocompleter;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.tagAutocompleter = mock(TagAutocompleter.class);
		this.undertest = new AutocompleteServlet(this.tagAutocompleter);
		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itReturnsSuggestionsForAddTag() throws Exception {
		this.req.setParameter("mode", "addtag");
		this.req.setParameter("fragment", "foo");

		final List<TagFrequency> res1 = listOfTagFrequency("foo", 2);
		final List<TagFrequency> res2 = listOfTagFrequency("barfoo", 3);
		when(this.tagAutocompleter.suggestTags("foo")).thenReturn(res1);
		when(this.tagAutocompleter.suggestFragments("foo")).thenReturn(res2);

		this.undertest.doGet(this.req, this.resp);

		assertEquals("["
				+ "{\"tag\":\"barfoo2\",\"count\":3},"
				+ "{\"tag\":\"barfoo1\",\"count\":2},"
				+ "{\"tag\":\"foo1\",\"count\":2},"
				+ "{\"tag\":\"barfoo0\",\"count\":1},"
				+ "{\"tag\":\"foo0\",\"count\":1}"
				+ "]",
				this.resp.getOutputAsString());
	}

	@Test
	public void itSuggestsForSearchEquals() throws Exception {
		setSearchParams("t=bar");
		when(this.tagAutocompleter.suggestTags("bar")).thenReturn(listOfTagFrequency("barfoo", 3));
		assertEqualSearchResult("");
	}

	@Test
	public void itSuggestsForSearchEqualsNegative() throws Exception {
		setSearchParams("-t=bar");
		when(this.tagAutocompleter.suggestTags("bar")).thenReturn(listOfTagFrequency("barfoo", 3));
		assertEqualSearchResult("-");
	}

	private void assertEqualSearchResult(final String resPrefix) throws ServletException, IOException {
		this.undertest.doGet(this.req, this.resp);
		assertEquals("["
				+ "{\"tag\":\"" + resPrefix + "t\\u003dbarfoo2\",\"count\":3},"
				+ "{\"tag\":\"" + resPrefix + "t\\u003dbarfoo1\",\"count\":2},"
				+ "{\"tag\":\"" + resPrefix + "t\\u003dbarfoo0\",\"count\":1}"
				+ "]",
				this.resp.getOutputAsString());
	}

	@Test
	public void itSuggestsForSearchEqualsWithSpacesAndBrackets() throws Exception {
		setSearchParams("t=bar");

		when(this.tagAutocompleter.suggestTags("bar")).thenReturn(Arrays.asList(new TagFrequency("bar foo", 3)));
		this.undertest.doGet(this.req, this.resp);
		assertEquals("[{\"tag\":\"t\\u003d\\\"bar foo\\\"\",\"count\":3}]", this.resp.getOutputAsString());

		this.resp = new MockHttpServletResponse();
		when(this.tagAutocompleter.suggestTags("bar")).thenReturn(Arrays.asList(new TagFrequency("bar(foo)", 3)));
		this.undertest.doGet(this.req, this.resp);
		assertEquals("[{\"tag\":\"t\\u003d\\\"bar(foo)\\\"\",\"count\":3}]", this.resp.getOutputAsString());
	}

	@Test
	public void itSuggestsForSearchPartial() throws Exception {
		setSearchParams("t~foo");
		when(this.tagAutocompleter.suggestTags("foo")).thenReturn(listOfTagFrequency("foobar", 1));
		when(this.tagAutocompleter.suggestFragments("foo")).thenReturn(listOfTagFrequency("barfoo", 3));
		assertPartialSearchResult("");
	}

	@Test
	public void itSuggestsForSearchPartialNegative() throws Exception {
		setSearchParams("-t~foo");
		when(this.tagAutocompleter.suggestTags("foo")).thenReturn(listOfTagFrequency("foobar", 1));
		when(this.tagAutocompleter.suggestFragments("foo")).thenReturn(listOfTagFrequency("barfoo", 3));
		assertPartialSearchResult("-");
	}

	private void setSearchParams(String fragment) {
		this.req.setParameter("mode", "search");
		this.req.setParameter("fragment", fragment);
	}

	private void assertPartialSearchResult(final String resPrefix) throws ServletException, IOException {
		this.undertest.doGet(this.req, this.resp);
		assertEquals("["
				+ "{\"tag\":\"" + resPrefix + "t\\u003dbarfoo2\",\"count\":3},"
				+ "{\"tag\":\"" + resPrefix + "t\\u003dbarfoo1\",\"count\":2},"
				+ "{\"tag\":\"" + resPrefix + "t\\u003dbarfoo0\",\"count\":1},"
				+ "{\"tag\":\"" + resPrefix + "t\\u003dfoobar0\",\"count\":1}"
				+ "]",
				this.resp.getOutputAsString());
	}

	private static List<TagFrequency> listOfTagFrequency(final String prefix, final int count) {
		final List<TagFrequency> l = new ArrayList<>(count);
		for (int i = count - 1; i >= 0; i--) {
			l.add(new TagFrequency(prefix + i, i + 1));
		}
		return l;
	}

}

package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

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
				this.resp.getContentAsString());
	}

	@Test
	public void itSuggestsForSearchEquals() throws Exception {
		this.req.setParameter("mode", "search");
		this.req.setParameter("fragment", "t=bar");

		final List<TagFrequency> res1 = listOfTagFrequency("barfoo", 3);
		when(this.tagAutocompleter.suggestTags("bar")).thenReturn(res1);

		this.undertest.doGet(this.req, this.resp);

		assertEquals("["
				+ "{\"tag\":\"t\\u003dbarfoo0\",\"count\":1},"
				+ "{\"tag\":\"t\\u003dbarfoo1\",\"count\":2},"
				+ "{\"tag\":\"t\\u003dbarfoo2\",\"count\":3}"
				+ "]",
				this.resp.getContentAsString());
	}

	@Test
	public void itSuggestsForSearch() throws Exception {
		this.req.setParameter("mode", "search");
		this.req.setParameter("fragment", "t~foo");

		final List<TagFrequency> res1 = listOfTagFrequency("barfoo", 3);
		when(this.tagAutocompleter.suggestFragments("foo")).thenReturn(res1);

		this.undertest.doGet(this.req, this.resp);

		assertEquals("["
				+ "{\"tag\":\"t\\u003dbarfoo0\",\"count\":1},"
				+ "{\"tag\":\"t\\u003dbarfoo1\",\"count\":2},"
				+ "{\"tag\":\"t\\u003dbarfoo2\",\"count\":3}"
				+ "]",
				this.resp.getContentAsString());
	}

	private static List<TagFrequency> listOfTagFrequency(final String prefix, final int count) {
		final List<TagFrequency> l = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			l.add(new TagFrequency(prefix + i, i + 1));
		}
		return l;
	}

}

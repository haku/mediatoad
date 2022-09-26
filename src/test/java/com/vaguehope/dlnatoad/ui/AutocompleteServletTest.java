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

import com.google.common.collect.ImmutableList;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.TagFrequency;

public class AutocompleteServletTest {

	private AutocompleteServlet undertest;
	private MediaDb mediaDb;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.mediaDb = mock(MediaDb.class);
		this.undertest = new AutocompleteServlet(this.mediaDb);
		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itReturnsSuggestionsForAddTag() throws Exception {
		this.req.setParameter("mode", "addtag");
		this.req.setParameter("fragment", "foo");

		final List<TagFrequency> res1 = listOfTagFrequency("foo", 2);
		final List<TagFrequency> res2 = ImmutableList.<TagFrequency>builder().addAll(res1).addAll(listOfTagFrequency("barfoo", 3)).build();
		when(this.mediaDb.getAutocompleteSuggestions("foo", 20, true)).thenReturn(res1);
		when(this.mediaDb.getAutocompleteSuggestions("foo", 22, false)).thenReturn(res2);

		this.undertest.doGet(this.req, this.resp);

		assertEquals("[{\"tag\":\"foo0\",\"count\":1},{\"tag\":\"foo1\",\"count\":2},"
				+ "{\"tag\":\"barfoo0\",\"count\":1},{\"tag\":\"barfoo1\",\"count\":2},{\"tag\":\"barfoo2\",\"count\":3}]",
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

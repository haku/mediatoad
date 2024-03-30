package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.auth.Permission;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.InMemoryMediaDb;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.TagAutocompleter;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MockContent;

public class ItemServletTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MockContent mockContent;
	private ItemServlet undertest;
	private MediaDb mediaDb;
	private TagAutocompleter tagAutocompleter;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.mediaDb = spy(new InMemoryMediaDb());
		this.tagAutocompleter = mock(TagAutocompleter.class);
		this.undertest = new ItemServlet(new ServletCommon(this.contentTree, null, null, true, null), this.contentTree, this.mediaDb, this.tagAutocompleter);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itRendersItem() throws Exception {
		givenReqForUnprotectedItem();

		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertThat(this.resp.getContentAsString(),
				containsString("<img src=\"../c/id00.mp4\">"));
		assertThat(this.resp.getContentAsString(), not(containsString("Add")));  // TODO make better along with following test.
	}

	@Test
	public void itRendersItemWithAddTagBox() throws Exception {
		givenReqForUnprotectedItem();
		givenUserWithEditTagsPermission();

		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		// TODO this assert could be much better.
		assertThat(this.resp.getContentAsString(), containsString("<input type=\"submit\" value=\"Add\">"));
	}

	@Test
	public void itRendersProtectedItemWithAddTagBox() throws Exception {
		final AuthList authlist = makeAuthListWithEdittags();
		givenReqForItem(authlist);
		givenUserWithEditTagsPermission();

		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		// TODO this assert could be much better.
		assertThat(this.resp.getContentAsString(), containsString("<input type=\"submit\" value=\"Add\">"));
	}

	@Test
	public void itDoesNotRenderProtectedItemWhenNoAuth() throws Exception {
		final AuthList authlist = mock(AuthList.class);
		final ContentItem item = givenReqForItem(authlist);

		this.req.setPathInfo("/" + item.getId());
		this.undertest.doGet(this.req, this.resp);
		assertEquals(401, this.resp.getStatus());

		this.resp = new MockHttpServletResponse();
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);
		this.undertest.doPost(this.req, this.resp);
		assertEquals(401, this.resp.getStatus());
	}

	@Test
	public void itBlocksAddingTagsIfNotPermitted() throws Exception {
		givenReqForUnprotectedItem();
		givenAddTagParams();

		this.undertest.doPost(this.req, this.resp);

		assertEquals("Forbidden\n", this.resp.getContentAsString());
		assertEquals(403, this.resp.getStatus());
		verifyNoInteractions(this.mediaDb);
		verifyNoInteractions(this.tagAutocompleter);
	}

	@Test
	public void itBlocksRemovingTagsIfNotPermitted() throws Exception {
		givenReqForUnprotectedItem();
		givenRmTagParams();

		this.undertest.doPost(this.req, this.resp);

		assertEquals("Forbidden\n", this.resp.getContentAsString());
		assertEquals(403, this.resp.getStatus());
		verifyNoInteractions(this.mediaDb);
	}

	@Test
	public void itBlocksAddingTagsToProtectedItemIfNotPermitted() throws Exception {
		final AuthList authlist = makeAuthList();
		givenReqForItem(authlist);
		givenAddTagParams();
		givenUserWithEditTagsPermission();

		this.undertest.doPost(this.req, this.resp);

		assertEquals("Forbidden\n", this.resp.getContentAsString());
		assertEquals(403, this.resp.getStatus());
		verifyNoInteractions(this.mediaDb);
		verifyNoInteractions(this.tagAutocompleter);
	}

	@Test
	public void itAddsTagToUnprotectedItem() throws Exception {
		final ContentItem item = givenReqForUnprotectedItem();
		final String tagToAdded = givenAddTagParams();
		givenUserWithEditTagsPermission();

		this.undertest.doPost(this.req, this.resp);

		assertEquals("Tag added.\n", this.resp.getContentAsString());
		assertEquals(303, this.resp.getStatus());
		assertEquals(tagToAdded, this.mediaDb.getTags(item.getId(), true, false).iterator().next().getTag());

		verify(this.tagAutocompleter).addOrIncrementTag(tagToAdded);
	}

	@Test
	public void itAddsTagToProtectedItemIfPermitted() throws Exception {
		final AuthList authlist = makeAuthListWithEdittags();
		final ContentItem item = givenReqForItem(authlist);
		final String tagToAdded = givenAddTagParams();
		givenUserWithEditTagsPermission();

		this.undertest.doPost(this.req, this.resp);

		assertEquals("Tag added.\n", this.resp.getContentAsString());
		assertEquals(303, this.resp.getStatus());
		assertEquals(tagToAdded, this.mediaDb.getTags(item.getId(), true, false).iterator().next().getTag());

		verify(this.tagAutocompleter).addOrIncrementTag(tagToAdded);
	}

	@Test
	public void itRemovesTagFromUnprotectedItem() throws Exception {
		final ContentItem item = givenReqForUnprotectedItem();
		final String tagToRm = givenRmTagParams();

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.addTag(item.getId(), tagToRm, 1);
		}
		givenUserWithEditTagsPermission();

		this.undertest.doPost(this.req, this.resp);

		assertEquals("Tags removed.\n", this.resp.getContentAsString());
		assertEquals(303, this.resp.getStatus());
		assertFalse(this.mediaDb.getTags(item.getId(), true, false).iterator().hasNext());

		verify(this.tagAutocompleter).decrementTag(tagToRm);
	}

	private static AuthList makeAuthList() {
		final AuthList authlist = mock(AuthList.class);
		when(authlist.hasUser("userfoo")).thenReturn(true);
		return authlist;
	}

	private static AuthList makeAuthListWithEdittags() {
		final AuthList authlist = makeAuthList();
		when(authlist.hasUserWithPermission("userfoo", Permission.EDITTAGS)).thenReturn(true);
		return authlist;
	}

	private ContentItem givenReqForUnprotectedItem() throws IOException {
		return givenReqForItem(null);
	}

	private ContentItem givenReqForItem(final AuthList authlist) throws IOException {
		final ContentNode dir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode(), authlist);
		final List<ContentItem> items = this.mockContent.givenMockItems(10, dir);

		final ContentItem item = items.get(0);
		this.req.setPathInfo("/" + item.getId());
		return item;
	}

	private void givenUserWithEditTagsPermission() {
		ReqAttr.USERNAME.set(this.req, "userfoo");
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);
	}

	private String givenAddTagParams() {
		final String tag = "mytag";
		this.req.setParameter("action", "addtag");
		this.req.setParameter("addTag", tag + " ");
		return tag;
	}

	private String givenRmTagParams() {
		this.req.setParameter("action", "rmtags");
		this.req.addParameter("b64tag", "bXl0YWc=:");
		return "mytag";
	}

}

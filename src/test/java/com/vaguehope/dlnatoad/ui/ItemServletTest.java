package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.InMemoryMediaDb;
import com.vaguehope.dlnatoad.db.MediaDb;
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

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.mediaDb = spy(new InMemoryMediaDb());
		this.undertest = new ItemServlet(new ServletCommon(this.contentTree, null, null, null, null), this.contentTree, this.mediaDb);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itRendersItem() throws Exception {
		givenReqForUnprotectedItem();

		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertThat(this.resp.getContentAsString(),
				containsString("<img style=\"max-width: 100%; max-height: 50em; padding-top: 1em;\" src=\"../c/id00.mp4\">"));
		assertThat(this.resp.getContentAsString(), not(containsString("Add")));  // TODO make better along with following test.
	}

	@Test
	public void itRendersItemWithAddTagBox() throws Exception {
		givenReqForUnprotectedItem();
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);

		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		// TODO this assert could be much better.
		assertThat(this.resp.getContentAsString(), containsString("<input type=\"submit\" value=\"Add\">"));
	}

	@Test
	public void itDoesNotRenderProtectedItem() throws Exception {
		final AuthList authlist = mock(AuthList.class);
		final ContentNode protecDir = this.mockContent.addMockDir("dir-protec", this.contentTree.getRootNode(), authlist);
		final List<ContentItem> protecItems = this.mockContent.givenMockItems(10, protecDir);

		this.req.setPathInfo("/" + protecItems.get(0).getId());
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
	public void itAddsTag() throws Exception {
		final String itemId = givenReqForUnprotectedItem();
		final String tagToAdded = givenAddTagParams();
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);

		this.undertest.doPost(this.req, this.resp);

		assertEquals("Tag added.\n", this.resp.getContentAsString());
		assertEquals(303, this.resp.getStatus());
		assertEquals(tagToAdded, this.mediaDb.getTags(itemId, false).iterator().next().getTag());
	}

	@Test
	public void itRemovesTag() throws Exception {
		final String itemId = givenReqForUnprotectedItem();
		final String tagToRm = givenRmTagParams();

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.addTag(itemId, tagToRm, 1);
		}
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);

		this.undertest.doPost(this.req, this.resp);

		assertEquals("Tag removed.\n", this.resp.getContentAsString());
		assertEquals(303, this.resp.getStatus());
		assertFalse(this.mediaDb.getTags(itemId, false).iterator().hasNext());
	}

	private String givenReqForUnprotectedItem() throws IOException {
		final ContentNode dir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode());
		final List<ContentItem> items = this.mockContent.givenMockItems(10, dir);
		final String itemId = items.get(0).getId();
		this.req.setPathInfo("/" + itemId);
		return itemId;
	}

	private String givenAddTagParams() {
		final String tag = "mytag";
		this.req.setParameter("action", "addtag");
		this.req.setParameter("tag", tag);
		return tag;
	}

	private String givenRmTagParams() {
		final String tag = "mytag";
		this.req.setParameter("action", "rmtag");
		this.req.setParameter("tag", tag);
		this.req.setParameter("cls", "");
		return tag;
	}

}

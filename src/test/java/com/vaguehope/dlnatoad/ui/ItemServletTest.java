package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.db.InMemoryMediaDb;
import com.vaguehope.dlnatoad.db.MediaDb;
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
		this.mediaDb = new InMemoryMediaDb();
		this.undertest = new ItemServlet(new ServletCommon(this.contentTree, null, null, null, null, null), this.contentTree, this.mediaDb);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
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
		this.undertest.doPost(this.req, this.resp);
		assertEquals(401, this.resp.getStatus());
	}

}

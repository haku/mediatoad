package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.dlnaserver.MockContent;
import com.vaguehope.dlnatoad.media.MediaId;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class IndexServletTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MockContent mockContent;
	private MediaId mediaId;
	private ImageResizer imageResizer;
	private IndexServlet undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree);
		this.mediaId = new MediaId(null);
		this.imageResizer = new ImageResizer(this.tmp.getRoot());
		this.undertest = new IndexServlet(this.contentTree, this.mediaId, this.imageResizer, "hostName");

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itReturns404WhenMixingUpItemsAndDirs() throws Exception {
		final List<ContentNode> mockItems = this.mockContent.givenMockItems(1);
		this.req.setPathInfo("/" + mockItems.get(0).getId());
		this.undertest.doGet(this.req, this.resp);
		assertEquals(404, this.resp.getStatus());
		assertEquals("Item is a not a directory: item 0\n", this.resp.getContentAsString());
	}

}

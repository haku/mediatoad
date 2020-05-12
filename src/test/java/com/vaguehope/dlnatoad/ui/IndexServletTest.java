package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentServingHistory;
import com.vaguehope.dlnatoad.dlnaserver.ContentServlet;
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
	private ContentServingHistory contentServingHistory;
	private IndexServlet undertest;
	private ContentServlet contentServlet;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree);
		this.mediaId = new MediaId(null);
		this.imageResizer = new ImageResizer(this.tmp.getRoot());
		this.contentServingHistory = new ContentServingHistory();
		this.contentServlet = mock(ContentServlet.class);
		this.undertest = new IndexServlet(this.contentTree, this.mediaId, this.imageResizer, "hostName", this.contentServingHistory, this.contentServlet);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itPassesThroughNotFound() throws Exception {
		this.req.setPathInfo("/foobar");
		this.undertest.doGet(this.req, this.resp);
		verify(this.contentServlet).service(this.req, this.resp);
	}

	@Test
	public void itPassesThroughItems() throws Exception {
		final List<ContentNode> mockItems = this.mockContent.givenMockItems(1);
		this.req.setPathInfo("/" + mockItems.get(0).getId());
		this.undertest.doGet(this.req, this.resp);
		verify(this.contentServlet).service(this.req, this.resp);
	}

	@Test
	public void itServesDirs() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode mockDir = mockDirs.get(0);
		this.req.setPathInfo("/" + mockDir.getId());
		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertThat(this.resp.getContentAsString(), containsString("<h3>" + mockDir.getTitle()));
	}

	@Test
	public void itServesRecent() throws Exception {
		this.mockContent.givenMockItems(1);
		this.req.setPathInfo("/" + ContentGroup.RECENT.getId());
		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		System.out.println(this.resp.getContentAsString());
		assertThat(this.resp.getContentAsString(), containsString("<h3>Recent"));
	}

}

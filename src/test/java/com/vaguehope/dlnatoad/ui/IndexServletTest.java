package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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

	private static final String PROPFIND_REQUEST_BODY_CADAVER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
			"<propfind xmlns=\"DAV:\"><prop>\n" +
			"<getcontentlength xmlns=\"DAV:\"/>\n" +
			"<getlastmodified xmlns=\"DAV:\"/>\n" +
			"<executable xmlns=\"http://apache.org/dav/props/\"/>\n" +
			"<resourcetype xmlns=\"DAV:\"/>\n" +
			"<checked-in xmlns=\"DAV:\"/>\n" +
			"<checked-out xmlns=\"DAV:\"/>\n" +
			"</prop></propfind>\n";

	private static final String PROPFIND_REQUEST_BODY_GNOME = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
			" <D:propfind xmlns:D=\"DAV:\">\n" +
			"  <D:prop>\n" +
			"<D:resourcetype/>\n" +
			"<D:getcontentlength/>\n" +
			"  </D:prop>\n" +
			" </D:propfind>";

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
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.mediaId = new MediaId(null);
		this.imageResizer = new ImageResizer(this.tmp.getRoot());
		this.contentServingHistory = new ContentServingHistory();
		this.contentServlet = mock(ContentServlet.class);
		this.undertest = new IndexServlet(this.contentTree, this.mediaId, this.imageResizer, "hostName", this.contentServingHistory, this.contentServlet, true);

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
		assertThat(this.resp.getContentAsString(), containsString("<h3>Recent"));
	}

	@Test
	public void itHandlesWebdavPropfindDepth0() throws Exception {
		this.req.setMethod("PROPFIND");
		this.req.setPathInfo("/");
		this.req.addHeader("Depth", "0");

		this.undertest.service(this.req, this.resp);

		assertThat(this.resp.getStatus(), equalTo(207));
		assertThat(this.resp.getContentType(), equalTo("application/xml"));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/</D:href>"));
	}

	@Test
	public void itHandlesWebdavPropfindDepth1() throws Exception {
		this.mockContent.givenMockItems(1);

		this.req.setMethod("PROPFIND");
		this.req.setPathInfo("/");
		this.req.addHeader("Depth", "1");

		this.undertest.service(this.req, this.resp);

		assertThat(this.resp.getStatus(), equalTo(207));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/</D:href>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/0-recent</D:href>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/id0</D:href>"));
	}

	@Test
	public void itHandlesWebdavPropfindSubdirDepth0() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode dir = mockDirs.get(0);
		assertTrue(dir.getFile().setLastModified(1234567890000L));
		dir.reload();

		this.req.setMethod("PROPFIND");
		this.req.setPathInfo("/" + dir.getId() + "/");
		this.req.addHeader("Depth", "0");

		this.undertest.service(this.req, this.resp);

		assertThat(this.resp.getStatus(), equalTo(207));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/dir 0/</D:href>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:getlastmodified>Fri, 13 Feb 2009 23:31:30 GMT</D:getlastmodified>"));
	}

	@Test
	public void itHandlesWebdavPropfindSubdirDepth1() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode dir = mockDirs.get(0);
		this.mockContent.addMockItem("i", dir);

		this.req.setMethod("PROPFIND");
		this.req.setPathInfo("/" + dir.getId() + "/");
		this.req.addHeader("Depth", "1");

		this.undertest.service(this.req, this.resp);

		assertThat(this.resp.getStatus(), equalTo(207));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/dir 0/</D:href>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/dir 0/i</D:href>"));
	}

	@Test
	public void itHandlesWebdavPropfindSubdirItem() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode dir = mockDirs.get(0);

		final ContentNode item = this.mockContent.addMockItem("i", dir);
		assertTrue(item.getFile().setLastModified(1034567890000L));
		item.reload();

		this.req.setMethod("PROPFIND");
		this.req.setPathInfo("/" + dir.getId() + "/" + item.getId());
		this.req.addHeader("Depth", "0");

		this.undertest.service(this.req, this.resp);

		assertThat(this.resp.getStatus(), equalTo(207));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/dir 0/i</D:href>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:getcontenttype>video/mp4</D:getcontenttype>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:getlastmodified>Mon, 14 Oct 2002 04:58:10 BST</D:getlastmodified>"));
	}

}

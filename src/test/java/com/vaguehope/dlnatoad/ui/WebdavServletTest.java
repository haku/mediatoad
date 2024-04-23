package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MockContent;

public class WebdavServletTest {

	@SuppressWarnings("unused")
	private static final String PROPFIND_REQUEST_BODY_CADAVER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
			"<propfind xmlns=\"DAV:\"><prop>\n" +
			"<getcontentlength xmlns=\"DAV:\"/>\n" +
			"<getlastmodified xmlns=\"DAV:\"/>\n" +
			"<executable xmlns=\"http://apache.org/dav/props/\"/>\n" +
			"<resourcetype xmlns=\"DAV:\"/>\n" +
			"<checked-in xmlns=\"DAV:\"/>\n" +
			"<checked-out xmlns=\"DAV:\"/>\n" +
			"</prop></propfind>\n";

	@SuppressWarnings("unused")
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
	private WebdavServlet undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.undertest = new WebdavServlet(this.contentTree);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itHandlesOptionsRequest() throws Exception {
		this.req.setMethod("OPTIONS");
		this.req.setPathInfo("/");

		this.undertest.service(this.req, this.resp);

		assertThat(this.resp.getStatus(), equalTo(200));
		assertThat(this.resp.getHeader("Allow"), equalTo("GET,HEAD,PROPFIND"));
		assertThat(this.resp.getHeader("DAV"), equalTo("1"));
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
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/dir0/</D:href>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:getlastmodified>Fri, 13 Feb 2009 23:31:30 UTC</D:getlastmodified>"));
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
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/dir0/</D:href>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/dir0/i</D:href>"));
	}

	@Test
	public void itHandlesWebdavPropfindSubdirItem() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode dir = mockDirs.get(0);

		final ContentItem item = this.mockContent.addMockItem("i", dir);
		assertTrue(item.getFile().setLastModified(1034567890000L));
		item.reload();

		this.req.setMethod("PROPFIND");
		this.req.setPathInfo("/" + dir.getId() + "/" + item.getId());
		this.req.addHeader("Depth", "0");

		this.undertest.service(this.req, this.resp);

		assertThat(this.resp.getStatus(), equalTo(207));
		assertThat(this.resp.getContentAsString(), containsString("<D:href>/dir0/i</D:href>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:getcontenttype>video/mp4</D:getcontenttype>"));
		assertThat(this.resp.getContentAsString(), containsString("<D:getlastmodified>Mon, 14 Oct 2002 03:58:10 UTC</D:getlastmodified>"));
	}

}

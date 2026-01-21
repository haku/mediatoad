package mediatoad.ui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.common.servlet.MockHttpServletRequest;
import com.vaguehope.common.servlet.MockHttpServletResponse;

import mediatoad.FakeServletCommon;
import mediatoad.media.ContentItem;
import mediatoad.media.ContentNode;
import mediatoad.media.ContentServlet;
import mediatoad.media.ContentTree;
import mediatoad.media.MockContent;
import mediatoad.ui.DirServlet;
import mediatoad.ui.IndexServlet;

public class IndexServletTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MockContent mockContent;
	private IndexServlet undertest;
	private ContentServlet contentServlet;
	private DirServlet dirServlet;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.contentServlet = mock(ContentServlet.class);
		this.dirServlet = mock(DirServlet.class);
		this.undertest = new IndexServlet(this.contentTree, this.contentServlet, this.dirServlet, FakeServletCommon.make());

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
		final List<ContentItem> mockItems = this.mockContent.givenMockItems(1);
		this.req.setPathInfo("/" + mockItems.get(0).getId());
		this.undertest.doGet(this.req, this.resp);
		verify(this.contentServlet).service(this.req, this.resp);
	}

	@Test
	public void itPassesThroughDirs() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode mockDir = mockDirs.get(0);
		this.req.setPathInfo("/" + mockDir.getId());
		this.undertest.doGet(this.req, this.resp);

		verify(this.dirServlet).doGet(this.req, this.resp);
	}

}

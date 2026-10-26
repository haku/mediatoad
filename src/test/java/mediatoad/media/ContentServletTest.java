package mediatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.util.resource.Resource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import mediatoad.FakeServletCommon;
import mediatoad.TestsWithHttpServer;
import mediatoad.auth.AuthList;

@SuppressWarnings("resource")
public class ContentServletTest extends TestsWithHttpServer {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private ContentServlet undertest;

	private MockContent mockContent;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.undertest = new ContentServlet(this.contentTree, new ContentServingHistory(), FakeServletCommon.make(this.contentTree));
	}

	@Test
	public void itResolvesMediaResource() throws Exception {
		final ContentItem item = this.mockContent.givenMockItems(1).get(0);
		final Resource res = this.undertest.getResource("/" + item.getId());
		assertEquals(item.getFile().getName(), res.getFile().getName());
	}

	@Test
	public void itResolvesMediaResourceWithContentDir() throws Exception {
		final ContentItem item = this.mockContent.givenMockItems(1).get(0);
		final Resource res = this.undertest.getResource("/c/" + item.getId());
		assertEquals(item.getFile().getName(), res.getFile().getName());
	}

	@Test
	public void itResolvesMediaResourceWithAnyFileExtension() throws Exception {
		final ContentItem item = this.mockContent.givenMockItems(1).get(0);
		final Resource res = this.undertest.getResource("/" + item.getId() + ".foo");
		assertEquals(item.getFile().getName(), res.getFile().getName());
	}

	@Test
	public void itReturnsNullForNotFound() throws Exception {
		assertNull(this.undertest.getResource("/some_id"));
	}

	private void startServer() throws Exception {
		startServer(this.undertest, "/c/*");
	}

	@Test
	public void itChecksPermissions() throws Exception {
		startServer();

		final AuthList allowAuthlist = mock(AuthList.class);
		when(allowAuthlist.hasUser("shork")).thenReturn(true);

		final ContentNode dir1 = this.mockContent.addMockDir("dir1", allowAuthlist);
		final ContentItem item1 = this.mockContent.addMockItem("item1", dir1);

		assertRequestResponseCode("/c/" + item1.getId(), 401);

		setAuthUsername("shork");
		assertRequestResponseCode("/c/" + item1.getId(), 200);
	}

	@Test
	public void itSetsContetType() throws Exception {
		startServer();

		final ContentNode dir1 = this.mockContent.addMockDir("dir1");
		final ContentItem item1 = this.mockContent.addMockItem("item1", dir1);

		final HttpURLConnection conn = assertRequestResponseCode("/c/" + item1.getId(), 200);
		try {
			final Map<String, List<String>> headers = conn.getHeaderFields();
			final List<String> actualType = headers.get("Content-Type");
			assertThat("Content-Type header present", actualType, not(nullValue()));
			assertEquals("video/mp4", actualType.get(0));
		}
		finally {
			conn.getInputStream().close();
		}
	}

	@Test
	public void itWorksWithWebdavSearchResults() throws Exception {
		startServer(this.undertest, "/search/*");

		final AuthList allowAuthlist = mock(AuthList.class);
		when(allowAuthlist.hasUser("shork")).thenReturn(true);

		final ContentNode dir1 = this.mockContent.addMockDir("dir1", allowAuthlist);
		final ContentItem item1 = this.mockContent.addMockItem("item1", dir1);

		setAuthUsername("shork");
		assertRequestResponseCode("/search/t=foo/" + item1.getId(), 200);
	}

}

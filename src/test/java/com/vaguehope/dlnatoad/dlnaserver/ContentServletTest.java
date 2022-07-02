package com.vaguehope.dlnatoad.dlnaserver;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.media.MediaFormat;

public class ContentServletTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private ContentServlet undertest;

	private MockContent mockContent;
	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	private Server server;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.undertest = new ContentServlet(this.contentTree, new ContentServingHistory(), true);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
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
		this.server = new Server();

		final SelectChannelConnector connector = new SelectChannelConnector();
		connector.setPort(0); // auto-bind to available port
		this.server.addConnector(connector);

		final ServletContextHandler contextHandler = new ServletContextHandler();
		contextHandler.setContextPath("/");
		contextHandler.addServlet(new ServletHolder(this.undertest), "/");
		MediaFormat.addTo(contextHandler.getMimeTypes());
		this.server.setHandler(contextHandler);
		this.server.start();
	}

	@After
	public void stopServer() throws Exception {
		if (this.server != null)
			this.server.stop();
	}

	@Test
	public void itSetsContetType() throws Exception {
		startServer();

		final ContentNode dir1 = this.mockContent.addMockDir("dir1");
		final ContentItem item1 = this.mockContent.addMockItem("item1", dir1);

		final URL url = new URL("http://localhost:" + this.server.getConnectors()[0].getLocalPort() + "/" + item1.getId());
		final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		try {
			assertEquals(200, conn.getResponseCode());
			final Map<String, List<String>> headers = conn.getHeaderFields();
			final List<String> actualType = headers.get("Content-Type");
			assertThat("Content-Type header present", actualType, not(nullValue()));
			assertEquals("video/mp4", actualType.get(0));
		}
		finally {
			conn.getInputStream().close();
		}
	}

}

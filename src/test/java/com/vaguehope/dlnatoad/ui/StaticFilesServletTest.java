package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.util.RequestLoggingFilter;

public class StaticFilesServletTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private StaticFilesServlet undertest;
	private Server server;
	private String hostAddress;

	@Before
	public void before() throws Exception {
		this.undertest = new StaticFilesServlet(null);
	}

	@SuppressWarnings("resource")
	@Test
	public void itServesFile() throws Exception {
		startServer();
		final URL url = new URL("http://" + this.hostAddress + ":"
				+ ((ServerConnector) this.server.getConnectors()[0]).getLocalPort()
				+ "/w/test.txt");
		final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		assertEquals("desu\n", IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8));
		assertEquals(200, conn.getResponseCode());

		final Map<String, List<String>> headers = conn.getHeaderFields();
		final List<String> actualType = headers.get("Content-Type");
		assertThat("Content-Type header present", actualType, not(nullValue()));
		assertEquals("text/plain", actualType.get(0));
	}

	@Test
	public void itHandlesModifiedFile() throws Exception {
		final File root = this.tmp.newFolder("webroot");
		final String filename = "modified.txt";
		final File file = new File(root, filename);
		final CharSequence text = String.valueOf(System.currentTimeMillis());
		FileUtils.write(file, text, StandardCharsets.UTF_8);

		final long oldTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
		assertTrue(file.setLastModified(oldTime));

		this.undertest = new StaticFilesServlet(root);
		startServer();

		assertRequestResponseCode(filename, 0, 200);
		assertRequestResponseCode(filename, oldTime, 304);

		assertTrue(file.setLastModified(System.currentTimeMillis()));
		assertRequestResponseCode(filename, oldTime, 200);
	}

	private void assertRequestResponseCode(final String filename, final long ifModifiedSince, final int code) throws MalformedURLException, IOException {
		final URL url = new URL("http://" + this.hostAddress + ":"
				+ ((ServerConnector) this.server.getConnectors()[0]).getLocalPort()
				+ "/w/" + filename);
		final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		if (ifModifiedSince > 0) conn.setIfModifiedSince(ifModifiedSince);
		assertEquals(code, conn.getResponseCode());
	}

	@SuppressWarnings("resource")
	private void startServer() throws Exception {
		this.server = new Server();

		final ServerConnector connector = new ServerConnector(this.server);
		this.hostAddress = InetAddress.getLocalHost().getHostAddress();
		connector.setHost(this.hostAddress);
		connector.setPort(0); // auto-bind to available port
		this.server.addConnector(connector);

		final ServletContextHandler contextHandler = new ServletContextHandler();
		contextHandler.setContextPath("/");
		contextHandler.addFilter(new FilterHolder(new RequestLoggingFilter()), "/*", null);
		contextHandler.addServlet(new ServletHolder(this.undertest), "/w/*");
		this.server.setHandler(contextHandler);
		this.server.start();
	}

	@After
	public void stopServer() throws Exception {
		if (this.server != null)
			this.server.stop();
	}

}

package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StaticFilesServletTest {

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

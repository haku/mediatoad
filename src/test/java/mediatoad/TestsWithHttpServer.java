package mediatoad;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;

import com.vaguehope.common.servlet.RequestLoggingFilter;

import mediatoad.auth.ReqAttr;
import mediatoad.media.MediaFormat;

public class TestsWithHttpServer {

	private Server server;
	private String hostAddress;
	private MockAuthFilter authFilter;

	@SuppressWarnings("resource")
	public void startServer(final Servlet servlet, final String pathSpec) throws Exception {
		this.server = new Server();

		final ServerConnector connector = new ServerConnector(this.server);
		this.hostAddress = InetAddress.getLocalHost().getHostAddress();
		connector.setHost(this.hostAddress);
		connector.setPort(0); // auto-bind to available port
		this.server.addConnector(connector);

		final ServletContextHandler contextHandler = new ServletContextHandler();
		MediaFormat.addTo(contextHandler.getMimeTypes());
		contextHandler.setContextPath("/");
		RequestLoggingFilter.addTo(contextHandler);

		this.authFilter = new MockAuthFilter();
		contextHandler.addFilter(new FilterHolder(this.authFilter), "/*", null);

		contextHandler.addServlet(new ServletHolder(servlet), pathSpec);
		this.server.setHandler(contextHandler);
		this.server.start();
	}

	@After
	public void stopServer() throws Exception {
		if (this.server != null)
			this.server.stop();
	}

	public void setAuthUsername(final String username) {
		this.authFilter.username = username;
	}

	public HttpURLConnection assertRequestResponseCode(final String path, final int code) throws MalformedURLException, IOException {
		return assertRequestResponseCode(path, 0, code);
	}

	public HttpURLConnection assertRequestResponseCode(final String path, final long ifModifiedSince, final int code) throws MalformedURLException, IOException {
		if (!path.startsWith("/")) throw new IllegalArgumentException("Path must start with: /");

		final URL url = new URL("http://" + this.hostAddress + ":"
				+ ((ServerConnector) this.server.getConnectors()[0]).getLocalPort()
				+ path);
		final HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		if (ifModifiedSince > 0) conn.setIfModifiedSince(ifModifiedSince);
		assertEquals("Did not get expected HTTP status for: " + url, code, conn.getResponseCode());

		return conn;
	}

	private static class MockAuthFilter implements Filter {

		public String username = null;

		@Override
		public void doFilter(final ServletRequest req, final ServletResponse resp, final FilterChain chain) throws IOException, ServletException {
			if (this.username != null) ReqAttr.USERNAME.set(req, this.username);

			chain.doFilter(req, resp);
		}

	}

}

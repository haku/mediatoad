package com.vaguehope.dlnatoad.dlnaserver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContentServlet extends DefaultServlet {

	private static final long serialVersionUID = -4819786280597656455L;
	private static final Logger LOG = LoggerFactory.getLogger(ContentServlet.class);

	private final ContentTree contentTree; // NOSONAR
	private final boolean printAccessLog;

	public ContentServlet (final ContentTree contentTree, final boolean printAccessLog) {
		this.contentTree = contentTree;
		this.printAccessLog = printAccessLog;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		try {
			super.doGet(req, resp);
		}
		finally {
			if (this.printAccessLog) {
				final String ranges = join(req.getHeaders(HttpHeaders.RANGE), ",");
				if (ranges != null) {
					LOG.info("request: {} {} (r:{}) {}", resp.getStatus(), req.getRequestURI(), ranges, req.getRemoteAddr());
				}
				else {
					LOG.info("request: {} {} {}", resp.getStatus(), req.getRequestURI(), req.getRemoteAddr());
				}
			}
		}
	}

	@Override
	public Resource getResource (final String pathInContext) {
		try {
			final ContentNode node = this.contentTree.getNode(URLDecoder.decode(pathInContext.replaceFirst("/", ""), "UTF-8"));
			if (node != null && node.isItem()) {
				return Resource.newResource(node.getFile());
			}
		}
		catch (final MalformedURLException e) {
			LOG.warn("Failed to map resource '{}': {}", pathInContext, e.getMessage());
		}
		catch (final IOException e) {
			LOG.warn("Failed to serve resource '{}': {}", pathInContext, e.getMessage());
		}
		return null;
	}

	private static String join (final Enumeration<String> en, final String join) {
		if (en == null || !en.hasMoreElements()) return null;
		StringBuilder s = new StringBuilder(en.nextElement());
		while (en.hasMoreElements()) {
			s.append(join).append(en.nextElement());
		}
		return s.toString();
	}

}

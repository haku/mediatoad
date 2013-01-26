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

	public ContentServlet (final ContentTree contentTree) {
		this.contentTree = contentTree;
	}

	@Override
	protected void doGet (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			super.doGet(req, resp);
		}
		finally {
			String ranges = join(req.getHeaders(HttpHeaders.RANGE), ",");
			if (ranges != null) {
				LOG.info("request: {} {} (r:{})", resp.getStatus(), req.getRequestURI(), ranges);
			}
			else {
				LOG.info("request: {} {}", resp.getStatus(), req.getRequestURI());
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

	private static String join (Enumeration<String> en, String join) {
		if (en == null || !en.hasMoreElements()) return null;
		StringBuilder s = new StringBuilder(en.nextElement());
		while(en.hasMoreElements()) {
			s.append(join).append(en.nextElement());
		}
		return s.toString();
	}

}
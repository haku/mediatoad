package com.vaguehope.dlnatoad.media;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.ui.ServletCommon;
import com.vaguehope.dlnatoad.util.MyFileServlet;

public class ContentServlet extends MyFileServlet {

	private static final long serialVersionUID = -4819786280597656455L;
	private static final Logger LOG = LoggerFactory.getLogger(ContentServlet.class);

	private final ContentTree contentTree; // NOSONAR
	private final ContentServingHistory contentServingHistory;

	public ContentServlet (final ContentTree contentTree, final ContentServingHistory contentServingHistory) {
		super();
		this.contentTree = contentTree;
		this.contentServingHistory = contentServingHistory;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String remoteAddr = req.getRemoteAddr();
		final String requestURI = req.getRequestURI();
		try {
			this.contentServingHistory.recordStart(remoteAddr, requestURI);
			super.doGet(req, resp);
		}
		finally {
			this.contentServingHistory.recordEnd(remoteAddr, requestURI);
		}
	}

	@Override
	public Resource getResource (final String pathInContext) {
		if (pathInContext.endsWith(".gz")) return null;

		try {
			String id = URLDecoder.decode(pathInContext, "UTF-8");
			id = ServletCommon.idFromPath(id, null);
			final ContentItem item = this.contentTree.getItem(id);
			if (item != null) {
				return Resource.newResource(item.getFile());
			}
		}
		catch (final IOException e) {
			LOG.warn("Failed to serve resource '{}': {}", pathInContext, e.getMessage());
		}
		return null;
	}

}

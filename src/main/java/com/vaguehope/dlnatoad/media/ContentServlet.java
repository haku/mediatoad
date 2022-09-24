package com.vaguehope.dlnatoad.media;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpContent;
import org.eclipse.jetty.http.HttpContent.ContentFactory;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.ResourceHttpContent;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HttpHeaders;
import com.vaguehope.dlnatoad.ui.ServletCommon;
import com.vaguehope.dlnatoad.util.StringHelper;

public class ContentServlet extends DefaultServlet {

	private static final long serialVersionUID = -4819786280597656455L;
	private static final Logger LOG = LoggerFactory.getLogger(ContentServlet.class);

	private final ContentTree contentTree; // NOSONAR
	private final ContentServingHistory contentServingHistory;
	private final boolean printAccessLog;
	private final ResourceService resourceService;

	public ContentServlet (final ContentTree contentTree, final ContentServingHistory contentServingHistory, final boolean printAccessLog) {
		this(contentTree, contentServingHistory, printAccessLog, new ResourceService());
	}

	private ContentServlet (final ContentTree contentTree, final ContentServingHistory contentServingHistory, final boolean printAccessLog, final ResourceService resourceService) {
		super(resourceService);
		this.contentTree = contentTree;
		this.contentServingHistory = contentServingHistory;
		this.printAccessLog = printAccessLog;
		this.resourceService = resourceService;
	}

	@Override
	public void init() throws UnavailableException {
		super.init();
		final ServletContext servletContext = getServletContext();
		final ContextHandler contextHandler = initContextHandler(servletContext);
		final MimeTypes mimeTypes = contextHandler.getMimeTypes();
		this.resourceService.setContentFactory(new MyContentFactory(this, mimeTypes));
	}

	// This is to resolve mime type based on resource file name like Jetty 8 did.
	// Jetty 9's org.eclipse.jetty.server.ResourceContentFactory uses pathInContext.
	// pathInContext does not have any file extension.
	private static class MyContentFactory implements ContentFactory {

		private final ResourceFactory factory;
		private final MimeTypes mimeTypes;

		public MyContentFactory(final ResourceFactory factory, final MimeTypes mimeTypes) {
			this.factory = factory;
			this.mimeTypes = mimeTypes;
		}

		@Override
		public HttpContent getContent(final String pathInContext, final int maxBufferSize) throws IOException {
			final Resource res = this.factory.getResource(pathInContext);
			if (res == null || !res.exists() || res.isDirectory()) return null;

			// TODO can probably be replaced by MediaFormat.identify(resource.getName()).
			final String mt = this.mimeTypes.getMimeByExtension(res.getName());
			return new ResourceHttpContent(res, mt, maxBufferSize);
		}

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
			if (this.printAccessLog) {
				final String ranges = StringHelper.join(req.getHeaders(HttpHeaders.RANGE), ",");
				if (ranges != null) {
					LOG.info("{} {} {} (r:{}) {}", resp.getStatus(), req.getMethod(), requestURI, ranges, remoteAddr);
				}
				else {
					LOG.info("{} {} {} {}", resp.getStatus(), req.getMethod(), requestURI, remoteAddr);
				}
			}
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

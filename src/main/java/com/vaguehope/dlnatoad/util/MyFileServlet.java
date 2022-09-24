package com.vaguehope.dlnatoad.util;

import java.io.IOException;

import javax.servlet.UnavailableException;

import org.eclipse.jetty.http.HttpContent;
import org.eclipse.jetty.http.HttpContent.ContentFactory;
import org.eclipse.jetty.http.ResourceHttpContent;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

import com.vaguehope.dlnatoad.media.MediaFormat;

public abstract class MyFileServlet extends DefaultServlet {

	private static final long serialVersionUID = -2804764308475269897L;

	private final ResourceService resourceService;

	public MyFileServlet() {
		this(new ResourceService());
	}

	// This is so the ResourceService can be both passed to super() and assigned to a field.
	private MyFileServlet(final ResourceService resourceService) {
		super(resourceService);
		this.resourceService = resourceService;
	}

	@Override
	public void init() throws UnavailableException {
		super.init();
		// Overwrite ContentFactory set by super.init().
		this.resourceService.setContentFactory(new MyContentFactory(this));
	}

	// This is to resolve mime type based on resource file name like Jetty 8 did.
	// Jetty 9's org.eclipse.jetty.server.ResourceContentFactory uses pathInContext.
	// pathInContext does not have any file extension.
	private static class MyContentFactory implements ContentFactory {

		private final ResourceFactory factory;

		public MyContentFactory(final ResourceFactory factory) {
			this.factory = factory;
		}

		@Override
		public HttpContent getContent(final String pathInContext, final int maxBufferSize) throws IOException {
			final Resource res = this.factory.getResource(pathInContext);
			if (res == null || !res.exists() || res.isDirectory()) return null;

			final MediaFormat mt = MediaFormat.identify(res.getName());
			return new ResourceHttpContent(res, mt.getMime(), maxBufferSize);
		}

	}

}

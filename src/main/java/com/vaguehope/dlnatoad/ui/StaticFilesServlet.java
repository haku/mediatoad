package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;

public class StaticFilesServlet extends DefaultServlet {

	private static final String KEY_FILE = "test.txt";  // Used to find /wui dir.
	private static final Logger LOG = LoggerFactory.getLogger(StaticFilesServlet.class);
	private static final long serialVersionUID = 8760909554925645417L;

	private final Resource rootRes;

	public StaticFilesServlet() {
		super();

		final URL f = StaticFilesServlet.class.getClassLoader().getResource("wui/" + KEY_FILE);
		if (f == null) {
			throw new IllegalStateException("Unable to find wui directory.");
		}

		try {
			final URI rootUri = URI.create(f.toURI().toASCIIString().replaceFirst("/" + KEY_FILE + "$", "/"));
			this.rootRes = Resource.newResource(rootUri);
		}
		catch (final URISyntaxException | MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Resource getResource(final String pathInContext) {
		try {
			final String path = StringUtils.removeStartIgnoreCase(pathInContext, "/" + C.STATIC_FILES_PATH_PREFIX);
			return this.rootRes.addPath(path);
		}
		catch (final IOException e) {
			LOG.warn("Failed to serve static file '{}': {}", pathInContext, e.getMessage());
			return null;
		}
	}

}

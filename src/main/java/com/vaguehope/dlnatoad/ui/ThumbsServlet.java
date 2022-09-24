package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLDecoder;

import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.util.ImageResizer;
import com.vaguehope.dlnatoad.util.MyFileServlet;

public class ThumbsServlet extends MyFileServlet {

	private static final int THUMB_SIZE_PIXELS = 200;
	private static final float THUMB_QUALITY = 0.8f;

	private static final Logger LOG = LoggerFactory.getLogger(ThumbsServlet.class);
	private static final long serialVersionUID = 3640173607729364665L;

	private final ContentTree contentTree;
	private final ImageResizer imageResizer;

	public ThumbsServlet(final ContentTree contentTree, final ImageResizer imageResizer) {
		super();
		this.contentTree = contentTree;
		this.imageResizer = imageResizer;
	}

	@Override
	public Resource getResource(final String pathInContext) {
		if (pathInContext.endsWith(".gz")) return null;

		try {
			String id = URLDecoder.decode(pathInContext, "UTF-8");
			id = ServletCommon.idFromPath(id, null);
			final ContentItem item = this.contentTree.getItem(id);
			if (item != null) {
				final File thumbFile = this.imageResizer.resizeFile(item.getFile(), THUMB_SIZE_PIXELS, THUMB_QUALITY);
				return Resource.newResource(thumbFile);
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

}

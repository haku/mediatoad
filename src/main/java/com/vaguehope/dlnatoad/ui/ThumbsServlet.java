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
import com.vaguehope.dlnatoad.media.ThumbnailGenerator;
import com.vaguehope.dlnatoad.util.MyFileServlet;

public class ThumbsServlet extends MyFileServlet {

	private static final Logger LOG = LoggerFactory.getLogger(ThumbsServlet.class);
	private static final long serialVersionUID = 3640173607729364665L;

	private final ContentTree contentTree;
	private final ThumbnailGenerator thumbnailGenerator;
	private final ServletCommon servletCommon;

	public ThumbsServlet(final ContentTree contentTree, final ThumbnailGenerator thumbnailGenerator, final ServletCommon servletCommon) {
		super();
		this.contentTree = contentTree;
		this.thumbnailGenerator = thumbnailGenerator;
		this.servletCommon = servletCommon;
	}

	@Override
	public Resource getResource(final String pathInContext) {
		if (pathInContext.endsWith(".gz")) return null;

		try {
			String id = URLDecoder.decode(pathInContext, "UTF-8");
			id = this.servletCommon.idFromPath(id, null);
			final ContentItem item = this.contentTree.getItem(id);
			if (item != null) {
				// TODO read dir prefs for video_thumbs prefs.
				if (this.thumbnailGenerator.supported(item.getFormat().getContentGroup(), true)) {
					final File thumbFile = this.thumbnailGenerator.generate(item);
					return Resource.newResource(thumbFile);
				}

				return null;
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

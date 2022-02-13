package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentServingHistory;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MediaId;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class ServletCommon {

	private final ContentTree contentTree;
	private final MediaId mediaId;
	private final ImageResizer imageResizer;
	private final String hostName;
	private final ContentServingHistory contentServingHistory;

	public ServletCommon (final ContentTree contentTree, final MediaId mediaId, final ImageResizer imageResizer,
			final String hostName, final ContentServingHistory contentServingHistory) {
		this.contentTree = contentTree;
		this.mediaId = mediaId;
		this.imageResizer = imageResizer;
		this.hostName = hostName;
		this.contentServingHistory = contentServingHistory;
	}

	@SuppressWarnings("resource")
	public static void returnStatus (final HttpServletResponse resp, final int status, final String msg) throws IOException {
		resp.reset();
		resp.setContentType("text/plain");
		resp.setStatus(status);
		resp.getWriter().println(msg);
	}

	public static void setHtmlContentType(final HttpServletResponse resp) {
		resp.setContentType("text/html; charset=utf-8");
	}

	public void headerAndStartBody(final PrintWriter w) {
		this.headerAndStartBody(w, null);
	}

	public void headerAndStartBody(final PrintWriter w, String title) {
		w.println("<!DOCTYPE html>");
		w.println("<html>");
		w.println("<head>");
		w.println("<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\">");

		w.print("<title>");
		if (!StringUtils.isBlank(title)) {
			w.print(title);
			w.print(" - ");
		}
		w.print(C.METADATA_MODEL_NAME);
		w.print(" (");
		w.print(this.hostName);
		w.print(")");
		w.println("</title>");

		w.println("<meta name=\"viewport\" content=\"width=device-width, minimum-scale=1.0, maximum-scale=1.0\">");

		w.println("<style>");
		w.println("body, div, input, label, p, span {font-family: sans-serif;}");
		w.println("</style>");

		w.println("</head>");
		w.println("<body>");
	}

	public void endBody(final PrintWriter w) {
		w.println("</body></html>");
	}

	public void printLinkRow(final HttpServletRequest req, final PrintWriter w) {
		final String query = StringUtils.trimToEmpty(req.getParameter(SearchServlet.PARAM_QUERY));
		final String remote = StringUtils.trimToEmpty(req.getParameter(SearchServlet.PARAM_REMOTE));
		final String remoteChecked = StringUtils.isNotBlank(remote) ? "checked" : "";

		w.println("<a href=\"/\">Home</a>");
		w.println("<a href=\"upnp\">UPNP</a>");
		w.println("<form style=\"display:inline;\" action=\"search\" method=\"GET\">");
		w.println("<input type=\"text\" id=\"query\" name=\"query\" value=\"" + query + "\">");
		w.print("<input type=\"checkbox\" id=\"remote\" name=\"remote\" value=\"true\" " + remoteChecked + ">");
		w.println("<label for=\"remote\">remote</label>");
		w.println("<input type=\"submit\" value=\"Search\">");
		w.println("</form>");
	}

	public void printDirectoriesAndItems(final PrintWriter w, final ContentNode contentNode) throws IOException {
		w.print("<h3>");
		w.print(contentNode.getTitle());
		w.print(" (");
		w.print(contentNode.getChildContainerCount());
		w.print(" dirs, ");
		w.print(contentNode.getChildItemCount());
		w.println(" items)</h3><ul>");

		contentNode.withEachChildContainer(c -> appendDirectory(w, c));
		final List<ContentNode> imagesToThumb = new ArrayList<>();
		contentNode.applyContainer(c -> {
			imagesToThumb.addAll(appendItemsAndImagesAndGetImagesToThumb(w, c.getItems()));
			return null;
		});

		// This needs to be done OUTSIDE the contentNode lock held by applyContainer().
		appendImageThumbnails(w, imagesToThumb);

		w.println("</ul>");
	}

	public void printItemsAndImages(final PrintWriter w, final List<Item> items) throws IOException {
		w.print("<h3>Local items: ");
		w.print(items.size());
		w.println("</h3><ul>");
		final List<ContentNode> imagesToThumb = appendItemsAndImagesAndGetImagesToThumb(w, items);
		appendImageThumbnails(w, imagesToThumb);
		w.println("</ul>");
	}

	private List<ContentNode> appendItemsAndImagesAndGetImagesToThumb(final PrintWriter w, final List<Item> items) throws IOException {
		final List<ContentNode> imagesToThumb = new ArrayList<>();
		for (Item i : items) {
			final ContentNode node = this.contentTree.getNode(i.getId());
			if (this.imageResizer != null && node.getFormat().getContentGroup() == ContentGroup.IMAGE) {
				imagesToThumb.add(node);
			}
			else {
				appendItem(w, node);
			}
		}
		return imagesToThumb;
	}

	private static void appendDirectory(final PrintWriter w, final Container dir) {
		w.print("<li><a href=\"");
		w.print(dir.getId());
		w.print("\">");
		w.print(dir.getTitle());
		w.println("</a></li>");
	}

	private static void appendItem(final PrintWriter w, final ContentNode node) throws IOException {
		w.print("<li><a href=\"");
		w.print(node.getId());
		w.print(".");
		w.print(node.getFormat().getExt());
		w.print("\">");
		w.print(node.getFile().getName());
		w.print("</a> [<a href=\"");
		w.print(node.getId());
		w.print(".");
		w.print(node.getFormat().getExt());
		w.print("\" download=\"");
		w.print(node.getFile().getName());
		w.print("\">");

		final Res firstResource = node.applyItem(i -> i.getFirstResource());
		if (firstResource != null) {
			w.print(FileHelper.readableFileSize(firstResource.getSize()));
		}

		w.print("</a>]");

		node.withItem(i -> {
			final List<Res> ress = i.getResources();
			if (ress != null) {
				for (final Res res : ress) {
					final String duration = res.getDuration();
					if (duration != null && duration.length() > 0) {
						w.print(" (");
						w.print(duration);
						w.print(")");
						break;
					}
				}
			}
		});

		w.println("</li>");
	}

	private void appendImageThumbnails(final PrintWriter w, final List<ContentNode> imagesToThumb) throws IOException {
		for (final ContentNode node : imagesToThumb) {
			final File thumbFile = this.imageResizer.resizeFile(node.getFile(), 200, 0.8f);
			final String thumbId = this.mediaId.contentIdSync(ContentGroup.THUMBNAIL, thumbFile);
			this.contentTree.addNode(new ContentNode(thumbId, null, thumbFile, MediaFormat.JPEG));

			w.print("<span><a href=\"");
			w.print(node.getId());
			w.print(".");
			w.print(node.getFormat().getExt());
			w.print("\">");
			w.print("<img style=\"max-width: 6em; max-height: 5em; margin: 0.5em 0.5em 0 0.5em;\" src=\"");
			w.print(thumbId);
			w.print("\">");
			w.println("</a></span>");
		}
	}

	public void appendDebugFooter(final PrintWriter w) {
		w.print("<p>");
		w.print(this.contentServingHistory.getActiveCount());
		w.print(" active playbacks, ");
		w.print(this.contentServingHistory.getRecentlyActiveCount(TimeUnit.MINUTES.toSeconds(15)));
		w.print(" active in last 15 minutes.");
		w.println("</p>");

		w.print("<p>");
		w.print(this.contentTree.getNodeCount());
		w.println(" content nodes.</p>");
	}


}

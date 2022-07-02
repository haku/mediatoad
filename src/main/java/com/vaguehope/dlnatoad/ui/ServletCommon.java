package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.model.ModelUtil;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentItem;
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
	private final ExecutorService exSvc;

	public ServletCommon (final ContentTree contentTree, final MediaId mediaId, final ImageResizer imageResizer,
			final String hostName, final ContentServingHistory contentServingHistory, final ExecutorService exSvc) {
		this.contentTree = contentTree;
		this.mediaId = mediaId;
		this.imageResizer = imageResizer;
		this.hostName = hostName;
		this.contentServingHistory = contentServingHistory;
		this.exSvc = exSvc;
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

	public void headerAndStartBody(final PrintWriter w, final String title) {
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
		w.print(contentNode.getNodeCount());
		w.print(" dirs, ");
		w.print(contentNode.getItemCount());
		w.println(" items)</h3><ul>");

		contentNode.withEachNode(c -> appendDirectory(w, c));
		final List<ContentItem> imagesToThumb = new ArrayList<>();
		contentNode.withEachItem(i -> appendItemOrGetImageToThumb(w, i, imagesToThumb));
		appendImageThumbnails(w, imagesToThumb);

		w.println("</ul>");
	}

	public void printItemsAndImages(final PrintWriter w, final List<ContentItem> items) throws IOException {
		w.print("<h3>Local items: ");
		w.print(items.size());
		w.println("</h3><ul>");

		final List<ContentItem> imagesToThumb = new ArrayList<>();
		for (final ContentItem item : items) {
			appendItemOrGetImageToThumb(w, item, imagesToThumb);
		}
		appendImageThumbnails(w, imagesToThumb);

		w.println("</ul>");
	}

	private void appendItemOrGetImageToThumb(final PrintWriter w, final ContentItem item, final List<ContentItem> imagesToThumb) throws IOException {
		if (this.imageResizer != null && item.getFormat().getContentGroup() == ContentGroup.IMAGE) {
			imagesToThumb.add(item);
		}
		else {
			appendItem(w, item);
		}
	}

	private static void appendDirectory(final PrintWriter w, final ContentNode node) {
		w.print("<li><a href=\"");
		w.print(node.getId());
		w.print("\">");
		w.print(node.getTitle());
		w.println("</a></li>");
	}

	private static void appendItem(final PrintWriter w, final ContentItem item) throws IOException {
		w.print("<li><a href=\"");
		w.print(item.getId());
		w.print(".");
		w.print(item.getFormat().getExt());
		w.print("\">");
		w.print(item.getFile().getName());
		w.print("</a> [<a href=\"");
		w.print(item.getId());
		w.print(".");
		w.print(item.getFormat().getExt());
		w.print("\" download=\"");
		w.print(item.getFile().getName());
		w.print("\">");

		final long fileLength = item.getFileLength();
		if (fileLength > 0) {
			w.print(FileHelper.readableFileSize(fileLength));
		}

		w.print("</a>]");

		final long durationMillis = item.getDurationMillis();
		if (durationMillis > 0) {
			w.print(" (");
			final long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis);
			w.print(ModelUtil.toTimeString(durationSeconds));
			w.print(")");
		}

		w.println("</li>");
	}

	private void appendImageThumbnails(final PrintWriter w, final List<ContentItem> imagesToThumb) throws IOException {
		for (final ContentItem item : imagesToThumb) {
			final File thumbFile = this.imageResizer.resizeFile(item.getFile(), 200, 0.8f);
			final String thumbId = this.mediaId.contentIdSync(ContentGroup.THUMBNAIL, thumbFile, this.exSvc);
			this.contentTree.addItem(new ContentItem(thumbId, null, null, thumbFile, MediaFormat.JPEG));

			w.print("<span><a href=\"");
			w.print(item.getId());
			w.print(".");
			w.print(item.getFormat().getExt());
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

		w.print("<p>content: ");
		w.print(this.contentTree.getNodeCount());
		w.print(" nodes, ");
		w.print(this.contentTree.getItemCount());
		w.println(" items.</p>");
	}


}

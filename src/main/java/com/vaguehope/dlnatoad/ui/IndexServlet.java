package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentServingHistory;
import com.vaguehope.dlnatoad.dlnaserver.ContentServlet;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MediaId;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;
import com.vaguehope.dlnatoad.util.StringHelper;
import com.vaguehope.dlnatoad.util.ThreadSafeDateFormatter;

public class IndexServlet extends HttpServlet {

	private static final String DO_NOT_LOG_ATTR = "do_not_log";
	private static final ThreadSafeDateFormatter RFC1123_DATE = new ThreadSafeDateFormatter("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
	private static final Logger LOG = LoggerFactory.getLogger(IndexServlet.class);
	private static final long serialVersionUID = -8907271726001369264L;

	private final ContentTree contentTree;
	private final MediaId mediaId;
	private final ImageResizer imageResizer;
	private final String hostName;
	private final ContentServingHistory contentServingHistory;
	private final ContentServlet contentServlet;
	private final boolean printAccessLog;

	public IndexServlet (final ContentTree contentTree, final MediaId mediaId, final ImageResizer imageResizer,
			final String hostName, final ContentServingHistory contentServingHistory,
			final ContentServlet contentServlet, boolean printAccessLog) {
		this.contentTree = contentTree;
		this.mediaId = mediaId;
		this.imageResizer = imageResizer;
		this.hostName = hostName;
		this.contentServingHistory = contentServingHistory;
		this.contentServlet = contentServlet;
		this.printAccessLog = printAccessLog;
	}

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		try {
			if ("PROPFIND".equals(req.getMethod())) {
				doPropfind(req, resp);
				return;
			}
			super.service(req, resp);
		}
		finally {
			if (this.printAccessLog && req.getAttribute(DO_NOT_LOG_ATTR) == null) {
				// TODO should log getRequestURI() instead?
				LOG.info("{} {} {} {}", resp.getStatus(), req.getMethod(), req.getPathInfo(), req.getRemoteAddr());
			}
		}
	}

	@Override
	protected void doOptions(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		resp.setHeader("Allow", "GET,HEAD,PROPFIND");
		resp.setHeader("DAV", "1");
	}

	private ContentNode contentNodeFromPath(final HttpServletRequest req) {
		String id = req.getPathInfo();
		if (id == null || id.length() < 1
				|| "/".equals(id)
				|| "/dlnatoad".equals(id)
				|| "/dlnatoad/".equals(id)) {
			id = ContentGroup.ROOT.getId();
		}
		else {
			id = ContentServlet.contentNodeIdFromPath(id);
		}
		return this.contentTree.getNode(id);
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final ContentNode contentNode = contentNodeFromPath(req);
		// ContentServlet does extra parsing and Index only handles directories anyway.
		if (contentNode == null || !contentNode.hasContainer()) {
			req.setAttribute(DO_NOT_LOG_ATTR, Boolean.TRUE);
			this.contentServlet.service(req, resp);
			return;
		}

		printDir(resp, contentNode);
	}

	private void printDir (final HttpServletResponse resp, final ContentNode contentNode) throws IOException {
		if (!contentNode.hasContainer()) {
			returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Item is a not a directory: " + contentNode.getId());
			return;
		}


		resp.setContentType("text/html; charset=utf-8");
		final PrintWriter w = resp.getWriter();

		w.println("<html>");
		w.println("<head>");
		w.println("<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\">");

		w.print("<title>");
		w.print(C.METADATA_MODEL_NAME);
		w.print(" (");
		w.print(this.hostName);
		w.print(")");
		w.println("</title>");

		w.println("<meta name=\"viewport\" content=\"width=device-width, minimum-scale=1.0, maximum-scale=1.0\">");
		w.println("</head>");
		w.print("<body><h3>");
		w.print(contentNode.getTitle());
		w.print(" (");
		w.print(contentNode.getChildContainerCount());
		w.print(" dirs, ");
		w.print(contentNode.getChildItemCount());
		w.println(" items)</h3><ul>");

		contentNode.withEachChildContainer(c -> appendDirectory(w, c));

		final List<ContentNode> imagesToThumb = new ArrayList<ContentNode>();
		contentNode.withEachChildItem(i -> {
			final ContentNode node = this.contentTree.getNode(i.getId());
			if (this.imageResizer != null && node.getFormat().getContentGroup() == ContentGroup.IMAGE) {
				imagesToThumb.add(node);
			}
			else {
				appendItem(w, node);
			}
		});

		w.println("</ul>");

		appendImageThumbnails(w, imagesToThumb);
		appendDebugFooter(w);
		w.println("</html></body>");
	}

	private void appendDirectory(final PrintWriter w, final Container dir) {
		w.print("<li><a href=\"");
		w.print(dir.getId());
		w.print("\">");
		w.print(dir.getTitle());
		w.println("</a></li>");
	}

	private void appendItem(final PrintWriter w, final ContentNode node) throws IOException {
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

	private void appendDebugFooter(final PrintWriter w) {
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

	// http://www.webdav.org/specs/rfc4918.html
	protected void doPropfind(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final ContentNode contentNode = contentNodeFromPath(req);
		if (contentNode == null) {
			returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + req.getPathInfo());
			return;
		}

		final String depth = req.getHeader("Depth");
		if (depth == null) {
			returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing depth header.");
			return;
		}
		if (!"0".equals(depth) && !"1".equals(depth)) {
			returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Unsupported depth: " + depth);
			return;
		}

		resp.setStatus(207);
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/xml");
		final PrintWriter w = resp.getWriter();
		w.println("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
		w.println("<D:multistatus xmlns:D=\"DAV:\">");
		appendPropfindItem(req, w, contentNode, false);
		if ("1".equals(depth) && contentNode.hasContainer()) {
			contentNode.withEachChildContainer(c -> {
				appendPropfindItem(req, w, this.contentTree.getNode(c.getId()), true);
			});
			contentNode.withEachChildItem(i -> {
				appendPropfindItem(req, w, this.contentTree.getNode(i.getId()), true);
			});
		}
		w.println("</D:multistatus>");
	}

	private static void appendPropfindItem(final HttpServletRequest req, final PrintWriter w, final ContentNode node, boolean appendIdToPath) {
		String path = req.getPathInfo();
		if (appendIdToPath) {
			path = StringHelper.removeSuffix(path, "/") + "/" + node.getId();
		}

		w.println("<D:response>");
		w.println("<D:href>" + path + "</D:href>");
		w.println("<D:propstat>");
		w.println("<D:prop>");
		if (node.hasContainer()) {
			w.println("<D:resourcetype><D:collection/></D:resourcetype>");
		}
		else {
			w.println("<D:resourcetype/>");
		}

		w.print("<D:displayname>");
		w.print(StringEscapeUtils.escapeXml11(node.getTitle()));
		w.println("</D:displayname>");

		if (node.getFormat() != null) {
			w.print("<D:getcontenttype>");
			w.print(node.getFormat().getMime());
			w.println("</D:getcontenttype>");
		}

		final long fileLength = node.getFileLength();
		if (fileLength > 0) {
			w.print("<D:getcontentlength>");
			w.print(fileLength);
			w.println("</D:getcontentlength>");
		}

		final long lastModified = node.getLastModified();
		if (lastModified > 0) {
			w.print("<D:getlastmodified>");
			w.print(RFC1123_DATE.get().format(new Date(lastModified)));
			w.print("</D:getlastmodified>");
		}

		w.println("</D:prop>");
		w.println("<D:status>HTTP/1.1 200 OK</D:status>");
		w.println("</D:propstat>");
		w.println("</D:response>");
	}

	private static void returnStatus (final HttpServletResponse resp, final int status, final String msg) throws IOException {
		resp.reset();
		resp.setContentType("text/plain");
		resp.setStatus(status);
		resp.getWriter().println(msg);
	}

}

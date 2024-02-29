package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;

import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentServlet;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.util.StringHelper;
import com.vaguehope.dlnatoad.util.ThreadSafeDateFormatter;

public class IndexServlet extends HttpServlet {

	private static final ThreadSafeDateFormatter RFC1123_DATE = new ThreadSafeDateFormatter("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
	private static final long serialVersionUID = -8907271726001369264L;

	private final ContentTree contentTree;
	private final ContentServlet contentServlet;
	private final DirServlet dirServlet;

	public IndexServlet(
			final ContentTree contentTree,
			final ContentServlet contentServlet,
			final DirServlet dirServlet) {
		this.contentTree = contentTree;
		this.contentServlet = contentServlet;
		this.dirServlet = dirServlet;
	}

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if ("PROPFIND".equals(req.getMethod())) {
			doPropfind(req, resp);
			return;
		}
		super.service(req, resp);
	}

	@Override
	protected void doOptions(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		resp.setHeader("Allow", "GET,HEAD,PROPFIND");
		resp.setHeader("DAV", "1");
	}

	private static String idFromPath(final HttpServletRequest req) {
		return ServletCommon.idFromPath(req.getPathInfo(), ContentGroup.ROOT.getId());
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String id = idFromPath(req);
		final ContentNode contentNode = this.contentTree.getNode(id);
		// ContentServlet does extra parsing and Index only handles directories anyway.
		if (contentNode == null) {
			this.contentServlet.service(req, resp);
			return;
		}

		req.setAttribute(DirServlet.PROXIED_FROM_INDEX_ATTR, Boolean.TRUE);
		this.dirServlet.doGet(req, resp);
	}

	// TODO move WebDav stuff somewhere else?

	// http://www.webdav.org/specs/rfc4918.html
	protected void doPropfind(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String id = idFromPath(req);
		final ContentNode node = this.contentTree.getNode(id);
		final ContentItem item = node != null ? null : this.contentTree.getItem(id);
		final String username = ReqAttr.USERNAME.get(req);

		if (node == null && item == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + req.getPathInfo());
			return;
		}

		if (node != null && !node.isUserAuth(username)) {
			ServletCommon.returnDenied(resp, username);
			return;
		}

		final String depth = req.getHeader("Depth");
		if (depth == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing depth header.");
			return;
		}
		if (!"0".equals(depth) && !"1".equals(depth)) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Unsupported depth: " + depth);
			return;
		}

		resp.setStatus(207);
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/xml");

		@SuppressWarnings("resource")
		final PrintWriter w = resp.getWriter();
		w.println("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
		w.println("<D:multistatus xmlns:D=\"DAV:\">");

		if (node != null) {
			appendPropfindNode(req, username, w, node, false);
			if ("1".equals(depth)) {
				node.withEachNode(n -> appendPropfindNode(req, username, w, n, true));
				node.withEachItem(i -> appendPropfindItem(req, w, i, true));
			}
		}
		else {
			appendPropfindItem(req, w, item, false);
		}
		w.println("</D:multistatus>");
	}

	private static void appendPropfindNode(final HttpServletRequest req, final String username, final PrintWriter w, final ContentNode node, final boolean appendIdToPath) {
		if (!node.isUserAuth(username)) return;

		String path = req.getPathInfo();
		if (appendIdToPath) {
			path = StringHelper.removeSuffix(path, "/") + "/" + node.getId();
		}

		w.println("<D:response>");
		w.println("<D:href>" + path + "</D:href>");
		w.println("<D:propstat>");
		w.println("<D:prop>");
		w.println("<D:resourcetype><D:collection/></D:resourcetype>");

		w.print("<D:displayname>");
		w.print(StringEscapeUtils.escapeXml11(node.getTitle()));
		w.println("</D:displayname>");

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

	private static void appendPropfindItem(final HttpServletRequest req, final PrintWriter w, final ContentItem item, final boolean appendIdToPath) {
		String path = req.getPathInfo();
		if (appendIdToPath) {
			path = StringHelper.removeSuffix(path, "/") + "/" + item.getId();
		}

		w.println("<D:response>");
		w.println("<D:href>" + path + "</D:href>");
		w.println("<D:propstat>");
		w.println("<D:prop>");
		w.println("<D:resourcetype/>");

		w.print("<D:displayname>");
		w.print(StringEscapeUtils.escapeXml11(item.getTitle()));
		w.println("</D:displayname>");

		if (item.getFormat() != null) {
			w.print("<D:getcontenttype>");
			w.print(item.getFormat().getMime());
			w.println("</D:getcontenttype>");
		}

		final long fileLength = item.getFileLength();
		if (fileLength > 0) {
			w.print("<D:getcontentlength>");
			w.print(fileLength);
			w.println("</D:getcontentlength>");
		}

		final long lastModified = item.getLastModified();
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

}

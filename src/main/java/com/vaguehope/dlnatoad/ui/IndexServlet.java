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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentItem;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentServlet;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.util.StringHelper;
import com.vaguehope.dlnatoad.util.ThreadSafeDateFormatter;

public class IndexServlet extends HttpServlet {

	private static final String DO_NOT_LOG_ATTR = "do_not_log";
	private static final ThreadSafeDateFormatter RFC1123_DATE = new ThreadSafeDateFormatter("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
	private static final Logger LOG = LoggerFactory.getLogger(IndexServlet.class);
	private static final long serialVersionUID = -8907271726001369264L;

	private ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final ContentServlet contentServlet;
	private final boolean printAccessLog;

	public IndexServlet (ServletCommon servletCommon, final ContentTree contentTree, final ContentServlet contentServlet, boolean printAccessLog) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
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

	private static String idFromPath(final HttpServletRequest req) {
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
		return id;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String id = idFromPath(req);
		final ContentNode contentNode = this.contentTree.getNode(id);
		// ContentServlet does extra parsing and Index only handles directories anyway.
		if (contentNode == null) {
			req.setAttribute(DO_NOT_LOG_ATTR, Boolean.TRUE);
			this.contentServlet.service(req, resp);
			return;
		}

		printDir(req, resp, contentNode);
	}

	private void printDir (HttpServletRequest req, final HttpServletResponse resp, final ContentNode contentNode) throws IOException {
		ServletCommon.setHtmlContentType(resp);
		@SuppressWarnings("resource")
		final PrintWriter w = resp.getWriter();
		this.servletCommon.headerAndStartBody(w);
		this.servletCommon.printLinkRow(req, w);

		this.servletCommon.printDirectoriesAndItems(w, contentNode);
		this.servletCommon.appendDebugFooter(w);
		this.servletCommon.endBody(w);
	}

	// http://www.webdav.org/specs/rfc4918.html
	protected void doPropfind(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String id = idFromPath(req);
		final ContentNode node = this.contentTree.getNode(id);
		final ContentItem item = node != null ? null : this.contentTree.getItem(id);

		if (node == null && item == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + req.getPathInfo());
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
			appendPropfindNode(req, w, node, false);
			if ("1".equals(depth)) {
				node.withEachNode(n -> appendPropfindNode(req, w, n, true));
				node.withEachItem(i -> appendPropfindItem(req, w, i, true));
			}
		}
		else {
			appendPropfindItem(req, w, item, false);
		}
		w.println("</D:multistatus>");
	}

	private static void appendPropfindNode(final HttpServletRequest req, final PrintWriter w, final ContentNode node, boolean appendIdToPath) {
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

	private static void appendPropfindItem(final HttpServletRequest req, final PrintWriter w, final ContentItem item, boolean appendIdToPath) {
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

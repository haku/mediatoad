package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;

public class WebdavServlet extends HttpServlet {

	private static final long serialVersionUID = 440497706656450276L;

	private final ContentTree contentTree;

	public WebdavServlet(final ContentTree contentTree) {
		this.contentTree = contentTree;
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

	// http://www.webdav.org/specs/rfc4918.html
	protected void doPropfind(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String id = ServletCommon.idFromPath(req.getPathInfo(), ContentGroup.ROOT.getId());
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
			Webdav.appendPropfindNode(req, username, w, node, false);
			if ("1".equals(depth)) {
				node.withEachNode(n -> Webdav.appendPropfindNode(req, username, w, n, true));
				node.withEachItem(i -> Webdav.appendPropfindItem(req, w, i, true));
			}
		}
		else {
			Webdav.appendPropfindItem(req, w, item, false);
		}
		w.println("</D:multistatus>");
	}

}

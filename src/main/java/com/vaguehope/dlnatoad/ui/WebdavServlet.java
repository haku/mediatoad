package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;
import com.vaguehope.dlnatoad.db.search.SortColumn;
import com.vaguehope.dlnatoad.db.search.DbSearchParser.DbSearch;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.ui.RequestPaths.SearchPath;
import com.vaguehope.dlnatoad.util.StringHelper;

public class WebdavServlet extends HttpServlet {

	private static final int MAX_SEARCH_ITEMS = 1000;
	private static final long serialVersionUID = 440497706656450276L;

	private final ContentTree contentTree;
	private final MediaDb db;
	private final ServletCommon servletCommon;

	public WebdavServlet(final ContentTree contentTree, final MediaDb db, final ServletCommon servletCommon) {
		this.contentTree = contentTree;
		this.db = db;
		this.servletCommon = servletCommon;
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
		final String username = ReqAttr.USERNAME.get(req);

		final String depth = req.getHeader("Depth");
		if (depth == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing depth header.");
			return;
		}
		if (!"0".equals(depth) && !"1".equals(depth)) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Unsupported depth: " + depth);
			return;
		}

		if (req.getPathInfo().startsWith("/" + C.SEARCH_PATH_PREFIX)) {
			searchReq(req, resp, username, depth);
		}
		else {
			dirOrFileReq(req, resp, username, depth);
		}
	}

	@SuppressWarnings("resource")
	private void searchReq(final HttpServletRequest req, final HttpServletResponse resp, final String username, final String depth) throws IOException {
		final PrintWriter w = startXmlResp(resp);
		final String dirPath = StringHelper.removeSuffix(req.getRequestURI(), "/");

		final SearchPath searchPath = RequestPaths.parseSearchPath(req.getPathInfo());
		if (searchPath.query.length() > 0) {
			if (searchPath.file.length() > 0) {
				// TODO be less lazy.
				resp.reset();
				dirOrFileReq(req, resp, username, depth);
				return;
			}
			else {
				Webdav.appendPropfindDir(w, dirPath, searchPath.query, System.currentTimeMillis());
				if ("1".equals(depth)) {
					final List<ContentItem> results = runQuery(username, searchPath.query);
					for (final ContentItem i : results) {
						Webdav.appendPropfindItem(w, i, i.getId() + "." + i.getFormat().getExt());
					}
				}
			}
		}
		else {
			Webdav.appendPropfindDir(w, dirPath, "search", System.currentTimeMillis());
		}

		endXmlResp(w);
	}

	private List<ContentItem> runQuery(final String username, final String query) throws IOException {
		try {
			final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);
			final DbSearch search = DbSearchParser.parseSearch(query, authIds, SortColumn.FILE_PATH.asc());
			final List<String> ids = search.execute(this.db, MAX_SEARCH_ITEMS, 0);
			return this.contentTree.getItemsForIds(ids, username);
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}
	}

	private void dirOrFileReq(final HttpServletRequest req, final HttpServletResponse resp, final String username, final String depth) throws IOException {
		final String id = this.servletCommon.idFromPath(req.getPathInfo(), ContentGroup.ROOT.getId());
		final ContentNode node = this.contentTree.getNode(id);
		final ContentItem item = node != null ? null : this.contentTree.getItem(id);
		if (node == null && item == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + req.getPathInfo());
			return;
		}
		if (node != null && !node.isUserAuth(username)) {
			ServletCommon.returnDenied(resp, username);
			return;
		}

		@SuppressWarnings("resource")
		final PrintWriter w = startXmlResp(resp);

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

		endXmlResp(w);
	}

	private static PrintWriter startXmlResp(final HttpServletResponse resp) throws IOException {
		resp.setStatus(207);
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/xml");

		final PrintWriter w = resp.getWriter();
		w.println("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
		w.println("<D:multistatus xmlns:D=\"DAV:\">");
		return w;
	}

	private static void endXmlResp(final PrintWriter w) {
		w.println("</D:multistatus>");
	}

}

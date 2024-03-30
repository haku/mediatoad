package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;

import com.github.mustachejava.Mustache;
import com.google.common.net.UrlEscapers;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.DbCache;
import com.vaguehope.dlnatoad.db.TagFrequency;
import com.vaguehope.dlnatoad.db.search.DbSearchSyntax;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentItem.Order;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.ui.templates.NodeIndexScope;
import com.vaguehope.dlnatoad.ui.templates.PageScope;
import com.vaguehope.dlnatoad.ui.templates.ResultGroupScope;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class DirServlet extends HttpServlet {


	static final String PROXIED_FROM_INDEX_ATTR = "proxied_from_index";

	static final String PARAM_SORT = "sort";
	private static final int ITEMS_PER_PAGE = SearchServlet.MAX_RESULTS;

	private static final long serialVersionUID = 6207424145390666199L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final ImageResizer imageResizer;
	private final DbCache dbCache;
	private final Supplier<Mustache> nodeIndexTemplate;

	public DirServlet(final ServletCommon servletCommon, final ContentTree contentTree, final ImageResizer imageResizer, final DbCache dbCache) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.imageResizer = imageResizer;
		this.dbCache = dbCache;
		this.nodeIndexTemplate = servletCommon.mustacheTemplate("nodeindex.html");
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final ContentNode node = getNodeFromPath(req, resp);
		if (node == null) return;

		final String username = ReqAttr.USERNAME.get(req);
		if (!node.isUserAuth(username)) {
			ServletCommon.returnDenied(resp, username);
			return;
		}

		if (req.getPathInfo().endsWith(".zip")) {
			returnNodeAsZipFile(node, resp);
			return;
		}

		returnNodeAsHtml(req, resp, node, username);
	}

	@SuppressWarnings("resource")
	private void returnNodeAsHtml(final HttpServletRequest req, final HttpServletResponse resp, final ContentNode node, final String username) throws IOException {
		final String sortRaw = ServletCommon.readParamWithDefault(req, resp, PARAM_SORT, "");
		if (sortRaw == null) return;
		final Integer limit = ServletCommon.readIntParamWithDefault(req, resp, SearchServlet.PARAM_PAGE_LIMIT, ITEMS_PER_PAGE, i -> i > 0);
		if (limit == null) return;
		final Integer offset = ServletCommon.readIntParamWithDefault(req, resp, SearchServlet.PARAM_PAGE_OFFSET, 0, i -> i >= 0);
		if (offset == null) return;

		// If proxied from IndexServlet then paths are relative to root.
		final String pathPrefix = req.getAttribute(PROXIED_FROM_INDEX_ATTR) != null ? "" : "../";
		final PageScope pageScope = this.servletCommon.pageScope(req, node.getTitle(), pathPrefix);
		final List<ContentNode> nodesUserHasAuth = node.nodesUserHasAuth(username);
		final String listTitle = makeIndexTitle(node, nodesUserHasAuth);
		final long nodeTotalFileLength = node.getTotalFileLength();

		final List<ContentItem> allItems = node.getCopyOfItems();
		final Order sort = parseSort(sortRaw);
		if (sort != null) {
			allItems.sort(sort);
		}
		final List<ContentItem> pageItems = allItems.subList(offset, Math.min(allItems.size(), offset + limit));

		final String nextPagePath;
		if (offset + limit < allItems.size()) {
			final StringBuilder s = new StringBuilder("?");
			if (sort != null) s.append(PARAM_SORT + "=" + sortRaw);
			if (s.length() > 1) s.append("&");
			s.append(SearchServlet.PARAM_PAGE_LIMIT).append("=").append(limit);
			s.append("&").append(SearchServlet.PARAM_PAGE_OFFSET).append("=").append(offset + limit);
			nextPagePath = s.toString();
		}
		else {
			nextPagePath = null;
		}

		final ResultGroupScope resultScope = new ResultGroupScope(listTitle, null, nextPagePath, pageScope);

		final boolean isRoot = ContentGroup.ROOT.getId().equals(node.getId());
		final NodeIndexScope nodeIndexScope = new NodeIndexScope(
				resultScope,
				isRoot ? null : node.getParentId(),
				!isRoot,
				node.getId(),
				node.getFile() != null ? node.getFile().getName() : node.getTitle(),
				FileHelper.readableFileSize(nodeTotalFileLength));

		appendNodes(resultScope, nodesUserHasAuth);

		final String linkQuery = "?" + ItemServlet.PARAM_NODE_ID + "=" + node.getId()
				+ (sort != null ? "&" + PARAM_SORT + "=" + sortRaw : "");
		for (final ContentItem i : pageItems) {
			resultScope.addContentItem(i, linkQuery, this.imageResizer);
		}

		maybeAppendTopTags(nodeIndexScope, node, username);

		// TODO this should probable go somewhere more generic, like IndexServlet.
		if (ContentGroup.ROOT.getId().equals(node.getId())) {
			pageScope.setDebugfooter(this.servletCommon.debugFooter());
		}

		ServletCommon.setHtmlContentType(resp);
		this.nodeIndexTemplate.get().execute(resp.getWriter(), new Object[] { pageScope, nodeIndexScope }).flush();
	}

	private static String makeIndexTitle(final ContentNode node, final List<ContentNode> nodesUserHasAuth) {
		final int nodeCount = nodesUserHasAuth.size();
		final int itemCount = node.getItemCount();
		String listTitle = node.getTitle() + " (";
		if (nodeCount > 0) {
			listTitle += nodeCount + " dirs";
		}
		if (itemCount > 0) {
			if (nodeCount > 0) listTitle += ", ";
			listTitle += itemCount + " items";
		}
		listTitle += ")";
		return listTitle;
	}

	private static void appendNodes(final ResultGroupScope resultScope, final List<ContentNode> nodesUserHasAuth) {
		for (final ContentNode n : nodesUserHasAuth) {
			resultScope.addLocalItem(C.DIR_PATH_PREFIX + n.getId(), n.getTitle());
		}
	}

	static Order parseSort(final String sortRaw) {
		if ("modified".equalsIgnoreCase(sortRaw)) {
			return ContentItem.Order.MODIFIED_DESC;
		}
		return null;
	}

	private void maybeAppendTopTags(final NodeIndexScope nodeIndexScope, final ContentNode node, final String username) throws IOException {
		if (this.dbCache == null) return;

		final File dir = node.getFile();
		final String pathPrefix = dir != null ? dir.getAbsolutePath() : null;
		if (pathPrefix == null && !ContentGroup.ROOT.getId().equals(node.getId())) return;

		final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);
		try {
			final List<TagFrequency> topTags = this.dbCache.getTopTags(authIds, pathPrefix);
			if (topTags.size() > 0) {
				for (final TagFrequency tag : topTags) {
					String query = DbSearchSyntax.makeSingleTagSearch(tag.getTag());
					if (node.getFile() != null) query += " " + DbSearchSyntax.makePathSearch(node.getFile());
					final String path = "search?query=" + StringEscapeUtils.escapeHtml4(UrlEscapers.urlFormParameterEscaper().escape(query));
					nodeIndexScope.addTopTag(path, tag.getTag(), tag.getCount());
				}
			}
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}
	}

	private static void returnNodeAsZipFile(final ContentNode node, final HttpServletResponse resp) throws IOException {
		resp.setContentType("application/zip");
		final ZipOutputStream zo = new ZipOutputStream(resp.getOutputStream());
		zo.setLevel(Deflater.NO_COMPRESSION);  // No point try to compress media files.

		node.withEachItem(i -> {
			final ZipEntry e = new ZipEntry(i.getFile().getName());
			e.setSize(i.getFileLength());
			e.setTime(i.getLastModified());
			zo.putNextEntry(e);
			FileUtils.copyFile(i.getFile(), zo);
			zo.closeEntry();
		});

		zo.flush();
		zo.close();
		resp.flushBuffer();
	}

	private ContentNode getNodeFromPath(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final String id = ServletCommon.idFromPath(req.getPathInfo(), ContentGroup.ROOT.getId());
		if (id == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "ID missing.");
			return null;
		}
		final ContentNode node = this.contentTree.getNode(id);
		if (node == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid ID.");
			return null;
		}
		return node;
	}

}

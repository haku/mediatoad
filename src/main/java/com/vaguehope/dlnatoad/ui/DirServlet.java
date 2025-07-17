package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.TagFrequency;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchSyntax;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentItem.Order;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.ThumbnailGenerator;
import com.vaguehope.dlnatoad.ui.templates.NodeIndexScope;
import com.vaguehope.dlnatoad.ui.templates.PageScope;
import com.vaguehope.dlnatoad.ui.templates.ResultGroupScope;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.GenTimer;

public class DirServlet extends HttpServlet {

	static final String PROXIED_FROM_INDEX_ATTR = "proxied_from_index";

	static final String PARAM_SORT = "sort";
	private static final int ITEMS_PER_PAGE = SearchServlet.MAX_RESULTS;

	static final String PREF_KEY_FAVOURITE = "favourite";
	private static final String PREF_KEY_SORT_MODIFIED = "sort_by_modified";
	private static final String PREF_KEY_VIDEO_THUMBS = "video_thumbs";

	private static final long serialVersionUID = 6207424145390666199L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final ThumbnailGenerator thumbnailGenerator;
	private final MediaDb db;
	private final DbCache dbCache;
	private final Supplier<Mustache> nodeIndexTemplate;


	public DirServlet(final ServletCommon servletCommon, final ContentTree contentTree, final ThumbnailGenerator thumbnailGenerator, final MediaDb db, final DbCache dbCache) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.thumbnailGenerator = thumbnailGenerator;
		this.db = db;
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

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final ContentNode node = getNodeFromPath(req, resp);
		if (node == null) return;

		final String username = ReqAttr.USERNAME.get(req);
		if (!node.isUserAuth(username)) {
			ServletCommon.returnDenied(resp, username);
			return;
		}

		if ("setprefs".equals(req.getParameter("action"))) {
			setPrefs(node, req, resp);
		}
	}

	@SuppressWarnings("resource")
	private void returnNodeAsHtml(final HttpServletRequest req, final HttpServletResponse resp, final ContentNode node, final String username) throws IOException {
		final GenTimer genTimer = new GenTimer();

		final String sortRaw = ServletCommon.readParamWithDefault(req, resp, PARAM_SORT, "");
		if (sortRaw == null) return;
		final Integer limit = ServletCommon.readIntParamWithDefault(req, resp, SearchServlet.PARAM_PAGE_LIMIT, ITEMS_PER_PAGE, i -> i > 0);
		if (limit == null) return;
		final Integer offset = ServletCommon.readIntParamWithDefault(req, resp, SearchServlet.PARAM_PAGE_OFFSET, 0, i -> i >= 0);
		if (offset == null) return;

		// If proxied from IndexServlet then paths are relative to root.
		final String pathPrefix = req.getAttribute(PROXIED_FROM_INDEX_ATTR) != null ? "" : "../";
		final PageScope pageScope = this.servletCommon.pageScope(req, node.getTitle(), pathPrefix);
		if (node.getFile() != null) pageScope.setExtraQuery(DbSearchSyntax.makePathSearch(node.getFile()));

		final boolean isRoot = ContentGroup.ROOT.getId().equals(node.getId());
		final ResultGroupScope favouritesScope;
		final boolean favourite;
		final boolean sortModified;
		final boolean videoThumbs;
		if (isRoot) {
			genTimer.startSection("favs");

			favouritesScope = new ResultGroupScope("Favourites", null, null, pageScope);
			final List<ContentNode> favourites = readFavourites(username);
			appendNodes(favouritesScope, favourites);

			favourite = false;
			sortModified = false;
			videoThumbs = false;
		}
		else {
			genTimer.startSection("prefs");

			final Map<String, String> dirPrefs = readNodePrefs(node);
			favourite = Boolean.parseBoolean(dirPrefs.get(PREF_KEY_FAVOURITE));
			sortModified = Boolean.parseBoolean(dirPrefs.get(PREF_KEY_SORT_MODIFIED));
			videoThumbs = Boolean.parseBoolean(dirPrefs.get(PREF_KEY_VIDEO_THUMBS));

			favouritesScope = null;
			pageScope.setUpLinkPath(node.getParentId());
		}

		genTimer.startSection("auth");
		final List<ContentNode> nodesUserHasAuth = node.nodesUserHasAuth(username);

		genTimer.startSection("node");
		final String listTitle = makeIndexTitle(node, nodesUserHasAuth);
		final long nodeTotalFileLength = node.getTotalFileLength();

		final List<ContentItem> allItems = node.getCopyOfItems();
		final Order sort = sortModified ? ContentItem.Order.MODIFIED_DESC : parseSort(sortRaw);
		final String sortParam = paramForSort(sort);
		if (sort != null) {
			allItems.sort(sort);
		}
		final List<ContentItem> pageItems = allItems.subList(offset, Math.min(allItems.size(), offset + limit));

		final String nextPagePath;
		if (offset + limit < allItems.size()) {
			final StringBuilder s = new StringBuilder("?");
			s.append(sortParam);
			if (s.length() > 1) s.append("&");
			s.append(SearchServlet.PARAM_PAGE_LIMIT).append("=").append(limit);
			s.append("&").append(SearchServlet.PARAM_PAGE_OFFSET).append("=").append(offset + limit);
			nextPagePath = s.toString();
		}
		else {
			nextPagePath = null;
		}

		genTimer.startSection("scope");
		final ResultGroupScope resultScope = new ResultGroupScope(listTitle, null, nextPagePath, pageScope);
		final NodeIndexScope nodeIndexScope = new NodeIndexScope(
				favouritesScope,
				resultScope,
				!isRoot,
				this.db != null && ReqAttr.ALLOW_EDIT_DIR_PREFS.get(req),
				favourite,
				isRoot || sort == Order.MODIFIED_DESC,
				videoThumbs,
				node.getId(),
				node.getFile() != null ? node.getFile().getName() : node.getTitle(),
				FileHelper.readableFileSize(nodeTotalFileLength));

		appendNodes(resultScope, nodesUserHasAuth);

		genTimer.startSection("thumbs");
		final String linkQuery = "?" + ItemServlet.PARAM_NODE_ID + "=" + node.getId()
				+ (sort != null ? "&" + sortParam : "");
		for (final ContentItem i : pageItems) {
			resultScope.addContentItem(i, linkQuery, this.thumbnailGenerator, videoThumbs);
		}

		genTimer.startSection("tags");
		maybeAppendTopTags(resultScope, node, username);

		// TODO this should probable go somewhere more generic, like IndexServlet.
		if (isRoot) {
			pageScope.setDebugfooter(this.servletCommon.debugFooter());
		}
		pageScope.appendToDebugFooter("gen: " + genTimer.summarise());

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

	private static void appendNodes(final ResultGroupScope scope, final List<ContentNode> nodes) {
		// TODO sort if sort mode set
		for (final ContentNode n : nodes) {
			scope.addLocalItem(C.DIR_PATH_PREFIX + n.getId(), n.getTitle());
		}
	}

	static Order parseSort(final String sortRaw) {
		if ("modified".equalsIgnoreCase(sortRaw)) {
			return ContentItem.Order.MODIFIED_DESC;
		}
		return null;
	}

	static String paramForSort(final Order order) {
		if (order == null) return "";
		switch (order) {
		case MODIFIED_DESC:
			return PARAM_SORT + "=" + "modified";
		default:
			return "";
		}
	}

	private void maybeAppendTopTags(final ResultGroupScope resultScope, final ContentNode node, final String username) throws IOException {
		if (this.dbCache == null) return;

		final File dir = node.getFile();
		final String pathPrefix = dir != null ? dir.getAbsolutePath() : null;
		if (pathPrefix == null && !ContentGroup.ROOT.getId().equals(node.getId())) return;

		final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);
		try {
			final List<TagFrequency> topTags = this.dbCache.dirTopTags(authIds, pathPrefix);
			addTagFrequenciesToScope(resultScope, node, topTags);
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}
	}

	static void addTagFrequenciesToScope(final ResultGroupScope resultScope, final ContentNode node, final List<TagFrequency> topTags) {
		if (topTags == null) {
			resultScope.setNoTagsMsg("(loading...)");
			return;
		}

		for (final TagFrequency tag : topTags) {
			String query = DbSearchSyntax.makeSingleTagSearch(tag.getTag());
			if (node != null && node.getFile() != null) query += " " + DbSearchSyntax.makePathSearch(node.getFile());
			final String path = "search?query=" + StringEscapeUtils.escapeHtml4(UrlEscapers.urlFormParameterEscaper().escape(query));
			resultScope.addTopTag(path, tag.getTag(), tag.getCount());
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

	private List<ContentNode> readFavourites(final String username) throws IOException {
		if (this.db == null) return Collections.emptyList();

		final Map<String, String> pathToValue;
		try {
			pathToValue = this.db.getAllNodePref(PREF_KEY_FAVOURITE);
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}

		final List<ContentNode> ret = new ArrayList<>();
		for (final Entry<String, String> e : pathToValue.entrySet()) {
			if (!Boolean.parseBoolean(e.getValue())) continue;

			final ContentNode node = this.contentTree.getNode(e.getKey());
			if (node == null || !node.isUserAuth(username)) continue;
			ret.add(node);
		}
		return ret;
	}

	private Map<String, String> readNodePrefs(final ContentNode node) throws IOException {
		if (node.getFile() == null || this.db == null) return Collections.emptyMap();
		try {
			return this.dbCache.nodePrefs(node.getId());
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}
	}

	private void setPrefs(final ContentNode node, final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		if (!ReqAttr.ALLOW_EDIT_DIR_PREFS.get(req) || node.getFile() == null) {
			ServletCommon.returnForbidden(resp);
			return;
		}

		final Boolean favourite = Boolean.valueOf(req.getParameter(PREF_KEY_FAVOURITE));
		final Boolean sortModified = Boolean.valueOf(req.getParameter(PREF_KEY_SORT_MODIFIED));
		final Boolean videoThumbs = Boolean.valueOf(req.getParameter(PREF_KEY_VIDEO_THUMBS));

		try (final WritableMediaDb w = this.db.getWritable()) {
			w.setNodePref(node.getId(), PREF_KEY_FAVOURITE, favourite.booleanValue() ? favourite.toString() : null);
			w.setNodePref(node.getId(), PREF_KEY_SORT_MODIFIED, sortModified.booleanValue() ? sortModified.toString() : null);
			w.setNodePref(node.getId(), PREF_KEY_VIDEO_THUMBS, videoThumbs.booleanValue() ? videoThumbs.toString() : null);
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}

		this.dbCache.invalidateNodePrefs(node.getId());

		resp.addHeader("Location", node.getId());
		ServletCommon.returnStatusWithoutReset(resp, HttpServletResponse.SC_SEE_OTHER, "Prefs saved.");
	}

	private ContentNode getNodeFromPath(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final String id = this.servletCommon.idFromPath(req.getPathInfo(), ContentGroup.ROOT.getId());
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

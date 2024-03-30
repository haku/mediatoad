package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.Mustache;
import com.google.common.net.UrlEscapers;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.Permission;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.FileData;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.TagAutocompleter;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;
import com.vaguehope.dlnatoad.db.search.DbSearchSyntax;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentItem.Order;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.ui.templates.ItemScope;
import com.vaguehope.dlnatoad.ui.templates.PageScope;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ThreadSafeDateFormatter;

public class ItemServlet extends HttpServlet {

	private static final int PREV_NEXT_SEARCH_DISTANCE = 10;
	static final String PARAM_NODE_ID = "node";
	private static final String PARAM_PREV_ID = "previd";
	private static final String PARAM_PREV_OFFSET = "prevoffset";
	private static final String PARAM_NEXT_ID = "nextid";
	private static final String PARAM_NEXT_OFFSET = "nextoffset";

	private static final ThreadSafeDateFormatter DATE_FORMAT = new ThreadSafeDateFormatter("yyyy-MM-dd HH:mm:ss z", Locale.ENGLISH);
	private static final Logger LOG = LoggerFactory.getLogger(ItemServlet.class);
	private static final long serialVersionUID = 3431697675845091273L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final MediaDb mediaDb;
	private final Supplier<Mustache> pageTemplate;
	private final TagAutocompleter tagAutocompleter;

	public ItemServlet(final ServletCommon servletCommon, final ContentTree contentTree, final MediaDb mediaDb, final TagAutocompleter tagAutocompleter) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
		this.tagAutocompleter = tagAutocompleter;
		this.pageTemplate = servletCommon.mustacheTemplate("item.html");
	}

	@SuppressWarnings("resource")
	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final ContentItem item = getItemFromPath(req, resp);
		if (item == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + req.getPathInfo());
			return;
		}

		final ContentNode node;
		final String nodeIdParam = ServletCommon.readParamWithDefault(req, resp, PARAM_NODE_ID, null);
		if (nodeIdParam != null) {
			node = this.contentTree.getNode(nodeIdParam);
			if (node != null && !node.hasItemWithId(item.getId())) {  // Otherwise node param would be a security bypass.
				ServletCommon.returnForbidden(resp);
				return;
			}
		}
		else {
			node = this.contentTree.getNode(item.getParentId());
		}

		final String username = ReqAttr.USERNAME.get(req);
		if (node == null || !node.isUserAuth(username)) {
			ServletCommon.returnDenied(resp, username);
			return;
		}

		final PageScope pageScope = this.servletCommon.pageScope(req, item.getTitle(), "../");
		final ItemScope itemScope = new ItemScope();
		final String editReqQueryParms = printPrevNextLinks(req, resp, item, node, username, itemScope);

		itemScope.img_path = "../" + C.CONTENT_PATH_PREFIX + item.getId() + "." + item.getFormat().getExt();
		itemScope.img_file_name = item.getFile().getName();
		itemScope.dir_path = "../" + node.getId();
		itemScope.dir_name = node.getTitle();

		if (this.mediaDb != null) {
			final boolean allowEditTags = ReqAttr.ALLOW_EDIT_TAGS.get(req) && (!node.hasAuthList() || node.isUserAuthWithPermission(username, Permission.EDITTAGS));
			final boolean editMode = allowEditTags && "true".equalsIgnoreCase(req.getParameter("edit"));
			itemScope.edit_tags = editMode;

			try {
				addTagsToScope(item, itemScope);
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}

			if (allowEditTags) {
				itemScope.tags_edit_path = "?edit=true" + editReqQueryParms;
				itemScope.tags_post_path = "?" + editReqQueryParms;
				itemScope.autofocus_add_tag = "addtag".equals(req.getParameter("autofocus"));
			}
		}

		if (this.mediaDb != null) {
			try {
				final FileData fileData = this.mediaDb.getFileData(item.getFile());
				if (fileData != null) {
					itemScope.details = item.getTitle();
					itemScope.details += "\n" + item.getWidth() + " × " + item.getHeight();
					itemScope.details += "\n" + FileHelper.readableFileSize(fileData.getSize());
					itemScope.details += "\n" + "MD5: " + fileData.getMd5();
					itemScope.details += "\n" + item.getFile().getAbsolutePath();
					itemScope.details += "\n" + "modified: " + DATE_FORMAT.get().format(new Date(item.getLastModified()));
				}
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
		}

		ServletCommon.setHtmlContentType(resp);
		this.pageTemplate.get().execute(resp.getWriter(), new Object[] { pageScope, itemScope }).flush();
	}

	/**
	 * Returns query params for paths to self for POSTs.
	 * return always starts with '?'.
	 */
	private String printPrevNextLinks(
			final HttpServletRequest req,
			final HttpServletResponse resp,
			final ContentItem item,
			final ContentNode node,
			final String username,
			final ItemScope itemScope) throws IOException {
		final String query = StringUtils.trimToNull(req.getParameter(SearchServlet.PARAM_QUERY));
		final String prevIdParam = ServletCommon.readParamWithDefault(req, resp, PARAM_PREV_ID, null);
		final String nextIdParam = ServletCommon.readParamWithDefault(req, resp, PARAM_NEXT_ID, null);
		final List<ContentItem> results;
		final Integer searchOffset;
		final String sortParam;
		if (query != null) {
			if (this.mediaDb == null) return "";

			final Integer prevOffsetParam = ServletCommon.readIntParamWithDefault(req, resp, PARAM_PREV_OFFSET, null, i -> i >= 0);
			final Integer nextOffsetParam = ServletCommon.readIntParamWithDefault(req, resp, PARAM_NEXT_OFFSET, null, i -> i >= 0);
			if ((prevIdParam != null && prevOffsetParam != null) || (nextIdParam != null && nextOffsetParam != null)) {
				return printPrevNextLinksHtml(itemScope, query, null, null, prevIdParam, nextIdParam, prevOffsetParam, nextOffsetParam);
			}

			final Integer offsetParam = ServletCommon.readIntParamWithDefault(req, resp, SearchServlet.PARAM_PAGE_OFFSET, 0, i -> i >= 0);
			if (offsetParam == null) return "";

			searchOffset = Math.max(offsetParam - PREV_NEXT_SEARCH_DISTANCE, 0);
			final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);
			final List<String> ids;
			try {
				ids = DbSearchParser.parseSearch(query, authIds, SearchServlet.RESULT_SORT_ORDER)
						.execute(this.mediaDb, PREV_NEXT_SEARCH_DISTANCE * 2, searchOffset);
			}
			catch (final SQLException e) {
				throw new IOException("Failed to make prev/next links: " + StringEscapeUtils.escapeHtml4(e.toString()));
			}
			results = this.contentTree.getItemsForIds(ids, username);

			sortParam = null;
		}
		else {
			final String sortRaw = ServletCommon.readParamWithDefault(req, resp, DirServlet.PARAM_SORT, "");
			if (sortRaw == null) return "";
			final Order sort = DirServlet.parseSort(sortRaw);
			sortParam = sort != null ? sortRaw : null;

			if (prevIdParam != null || nextIdParam != null) {
				return printPrevNextLinksHtml(itemScope, null, sortParam, node, prevIdParam, nextIdParam, null, null);
			}

			results = node.getCopyOfItems();
			if (sort != null) {
				results.sort(sort);
			}

			searchOffset = null;
		}

		int prevI = -1;
		int nextI = -1;
		for (int i = 0; i < results.size(); i++) {
			if (item.getId().equals(results.get(i).getId())) {
				prevI = i - 1;
				nextI = i + 1;
				break;
			}
		}

		ContentItem prevItem = null;
		ContentItem nextItem = null;
		if (prevI >= 0) {
			for (int i = prevI; i >= 0; i--) {
				final ContentItem ci = results.get(i);
				if (ci.getFormat().getContentGroup() != ContentGroup.IMAGE) continue;
				prevI = i;
				prevItem = ci;
				break;
			}
		}
		if (nextI >= 0 && nextI < results.size()) {
			for (int i = nextI; i < results.size(); i++) {
				final ContentItem ci = results.get(i);
				if (ci.getFormat().getContentGroup() != ContentGroup.IMAGE) continue;
				nextI = i;
				nextItem = ci;
				break;
			}
		}

		final String prevId = prevItem != null ? prevItem.getId() : null;
		final String nextId = nextItem != null ? nextItem.getId() : null;
		final Integer prevOffset = searchOffset != null ? searchOffset + prevI : null;
		final Integer nextOffset = searchOffset != null ? searchOffset + nextI : null;

		return printPrevNextLinksHtml(itemScope, query, sortParam, node, prevId, nextId, prevOffset, nextOffset);
	}

	/**
	 * return always starts with '?'.
	 */
	private static String printPrevNextLinksHtml(
			final ItemScope itemScope,
			final String query,
			final String sort,
			final ContentNode node,
			final String prevId,
			final String nextId,
			final Integer prevOffset,
			final Integer nextOffset) {
		final StringBuilder editReqQueryParms = new StringBuilder();
		final String linkQuery;
		final String allPath;
		final String allTitle;
		if (query != null) {
			final String searchQueryParam = SearchServlet.PARAM_QUERY + "="
					+ StringEscapeUtils.escapeHtml4(UrlEscapers.urlFormParameterEscaper().escape(query));
			editReqQueryParms.append("&").append(searchQueryParam);
			allPath = "../search?" + searchQueryParam;  // TODO extract path to constant.
			allTitle = "All Results";
			linkQuery = "?" + searchQueryParam + "&" + SearchServlet.PARAM_PAGE_OFFSET + "=";
		}
		else {
			editReqQueryParms.append("&").append(PARAM_NODE_ID).append("=").append(node.getId());

			final String sortParam;
			if (sort != null) {
				sortParam = DirServlet.PARAM_SORT + "=" + sort;
				editReqQueryParms.append("&").append(sortParam);
			}
			else {
				sortParam = null;
			}

			allPath = "../" + node.getId() + (sortParam != null ? "?" + sortParam : "");
			allTitle = StringEscapeUtils.escapeHtml4(node.getTitle());
			linkQuery = "?" + PARAM_NODE_ID + "=" + node.getId() + (sortParam != null ? "&" + sortParam : "");
		}

		if (prevId != null) {
			itemScope.previous_path = prevId;
			if (linkQuery != null) itemScope.previous_path += linkQuery;
			if (prevOffset != null) itemScope.previous_path += prevOffset;

			editReqQueryParms.append("&").append(PARAM_PREV_ID).append("=").append(prevId);
			if (prevOffset != null) editReqQueryParms.append("&").append(PARAM_PREV_OFFSET).append("=").append(prevOffset);
		}

		itemScope.up_path = allPath;
		itemScope.up_title = allTitle;

		if (nextId != null) {
			itemScope.next_path = nextId;
			if (linkQuery != null) itemScope.next_path += linkQuery;
			if (nextOffset != null) itemScope.next_path += nextOffset;

			editReqQueryParms.append("&").append(PARAM_NEXT_ID).append("=").append(nextId);
			if (nextOffset != null) editReqQueryParms.append("&").append(PARAM_NEXT_OFFSET).append("=").append(nextOffset);
		}

		return editReqQueryParms.toString();
	}

	private void addTagsToScope(final ContentItem item, final ItemScope itemScope) throws SQLException {
		final Collection<Tag> tags = this.mediaDb.getTags(item.getId(), false);
		for (final Tag tag : tags) {
			final String path = "../search?query=" + StringEscapeUtils.escapeHtml4(
					UrlEscapers.urlFormParameterEscaper().escape(
							DbSearchSyntax.makeSingleTagSearch(tag.getTag())));

			final Encoder encoder = Base64.getUrlEncoder().withoutPadding();
			final String b64tag = encoder.encodeToString(tag.getTag().getBytes(StandardCharsets.UTF_8))
					+ ":" + encoder.encodeToString(tag.getCls().getBytes(StandardCharsets.UTF_8));

			itemScope.addTag(tag.getTag(), tag.getCls(), path, b64tag);
		}
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (this.mediaDb == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Can not store tags when --db is not specified.");
			return;
		}

		if (!ReqAttr.ALLOW_EDIT_TAGS.get(req)) {
			ServletCommon.returnForbidden(resp);
			return;
		}

		final ContentItem item = getItemFromPath(req, resp);
		if (item == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + req.getPathInfo());
			return;
		}

		final ContentNode node = this.contentTree.getNode(item.getParentId());
		final String username = ReqAttr.USERNAME.get(req);
		if (node == null || !node.isUserAuth(username) || (node.hasAuthList() && !node.isUserAuthWithPermission(username, Permission.EDITTAGS))) {
			ServletCommon.returnDenied(resp, username);
			return;
		}

		if ("addtag".equalsIgnoreCase(req.getParameter("action"))) {
			final String tag = ServletCommon.readRequiredParam(req, resp, "addTag", 1);
			if (tag == null) return;
			try (final WritableMediaDb w = this.mediaDb.getWritable()) {
				w.addTag(item.getId(), tag, System.currentTimeMillis());
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
			LOG.info("{} added tag to {}: {}", username, item.getId(), tag);
			resp.addHeader("Location", item.getId() + ServletCommon.queryWithParam(req, "autofocus=addtag"));
			ServletCommon.returnStatusWithoutReset(resp, HttpServletResponse.SC_SEE_OTHER, "Tag added.");

			// TODO this should probably be integrated somewhere more central so that other tag changes are also taken into account.
			this.tagAutocompleter.addOrIncrementTag(tag);
		}
		else if ("rmtags".equalsIgnoreCase(req.getParameter("action"))) {
			final String[] b64tagsandclss = ServletCommon.readRequiredParams(req, resp, "b64tag", 1);
			if (b64tagsandclss == null) return;

			final String[] tags = new String[b64tagsandclss.length];
			final String[] clss = new String[tags.length];
			for (int i = 0; i < b64tagsandclss.length; i++) {
				final String b64tagandcls = b64tagsandclss[i];
				final int x = b64tagandcls.indexOf(":");
				if (x <= 0) {
					ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param invalid: " + b64tagandcls);
					return;
				}
				tags[i] = new String(Base64.getDecoder().decode(b64tagandcls.substring(0, x)), StandardCharsets.UTF_8);
				clss[i] = new String(Base64.getDecoder().decode(b64tagandcls.substring(x + 1)), StandardCharsets.UTF_8);
			}
			try (final WritableMediaDb w = this.mediaDb.getWritable()) {
				for (int i = 0; i < tags.length; i++) {
					w.setTagModifiedAndDeleted(item.getId(), tags[i], clss[i], true, System.currentTimeMillis());
				}
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
			// TODO decode b64tagsandclss.
			LOG.info("{} rm tags from {}: {}", username, item.getId(), Arrays.toString(b64tagsandclss));
			resp.addHeader("Location", item.getId() + ServletCommon.query(req));
			ServletCommon.returnStatusWithoutReset(resp, HttpServletResponse.SC_SEE_OTHER, "Tags removed.");

			// TODO this should probably be integrated somewhere more central so that other tag changes are also taken into account.
			for (final String tag : tags) {
				this.tagAutocompleter.decrementTag(tag);
			}
		}
		else {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid action.");
		}
	}

	private ContentItem getItemFromPath(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final String id = ServletCommon.idFromPath(req.getPathInfo(), null);
		if (id == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "ID missing.");
			return null;
		}
		final ContentItem item = this.contentTree.getItem(id);
		if (item == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid ID.");
			return null;
		}
		return item;
	}

}

package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.UrlEscapers;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.FileData;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.util.FileHelper;

public class ItemServlet extends HttpServlet {

	private static final int PREV_NEXT_SEARCH_DISTANCE = 10;
	static final String PARAM_NODE_ID = "node";
	private static final String PARAM_PREV_ID = "previd";
	private static final String PARAM_PREV_OFFSET = "prevoffset";
	private static final String PARAM_NEXT_ID = "nextid";
	private static final String PARAM_NEXT_OFFSET = "nextoffset";

	private static final Logger LOG = LoggerFactory.getLogger(ItemServlet.class);
	private static final long serialVersionUID = 3431697675845091273L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final MediaDb mediaDb;

	public ItemServlet(final ServletCommon servletCommon, final ContentTree contentTree, final MediaDb mediaDb) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
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

		ServletCommon.setHtmlContentType(resp);
		final PrintWriter w = resp.getWriter();
		this.servletCommon.headerAndStartBody(w, "../", item.getTitle());
		this.servletCommon.printLinkRow(req, w, "../");

		final String editReqQueryParms = printPrevNextLinks(req, resp, item, node, username, w);

		w.print("<h2>");
		w.print(StringEscapeUtils.escapeHtml4(item.getTitle()));
		w.println("</h2>");

		w.println("<div>");
		if (this.mediaDb != null) {
			final boolean allowEditTags = ReqAttr.ALLOW_EDIT_TAGS.get(req);
			final boolean editMode = allowEditTags && "true".equalsIgnoreCase(req.getParameter("edit"));
			try {
				if (editMode) {
					printEditTags(item, w);
				}
				else {
					printSimpleTags(item, w);
				}
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}

			if (allowEditTags) {
				w.println("<div style=\"padding-top: 0.5em; display: flex; justify-content: center;\">");
				w.print("<a style=\"padding-right: 0.5em;\" href=\"");
				w.print("?edit=true" + editReqQueryParms);
				w.println("\">Edit</a>");
				w.print("<form style=\"display:inline;\" action=\"?");
				w.print(editReqQueryParms);
				w.println("\" method=\"POST\">");
				w.println("<input type=\"hidden\" name=\"action\" value=\"addtag\">");
				w.println("<div class=\"autocomplete_wrapper addTag_wrapper\">");
				w.print("<input type=\"text\" id=\"addTag\" name=\"addTag\" value=\"\"");
				if ("addtag".equals(req.getParameter("autofocus"))) w.print(" autofocus");
				w.println(" style=\"width: 20em;\" autocomplete=\"off\" spellcheck=false autocorrect=\"off\" autocapitalize=\"off\">");
				w.println("</div>");
				w.println("<input type=\"submit\" value=\"Add\">");
				w.println("</form>");
				w.println("<script src=\"../w/autocomplete-addtag.js\"></script>");
				w.println("</div>");
			}
		}
		w.println("</div>");

		w.println("<div style=\"text-align: center;\">");
		w.print("<img style=\"max-width: 100%; max-height: 50em; padding-top: 1em;\" src=\"../");
		w.print(C.CONTENT_PATH_PREFIX);
		w.print(item.getId());
		w.print(".");
		w.print(item.getFormat().getExt());
		w.println("\">");
		w.println("</div>");

		w.print("<a href=\"../");
		w.print(C.CONTENT_PATH_PREFIX);
		w.print(item.getId());
		w.print(".");
		w.print(item.getFormat().getExt());
		w.print("\" download=\"");
		w.print(StringEscapeUtils.escapeHtml4(item.getFile().getName()));
		w.println("\">Download</a>");

		w.print("<a style=\"padding-left: 1em;\" href=\"../");
		w.print(node.getId());
		w.print("\">");
		w.print(StringEscapeUtils.escapeHtml4(node.getTitle()));
		w.println("</a>");

		if (this.mediaDb != null) {
			try {
				final FileData fileData = this.mediaDb.getFileData(item.getFile());
				if (fileData != null) {
					w.println("<pre>");
					w.println(FileHelper.readableFileSize(fileData.getSize()));
					w.print("MD5: ");
					w.println(fileData.getMd5());
					w.println("</pre>");
				}
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
		}

		this.servletCommon.endBody(w);
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
			final PrintWriter w) throws IOException {
		final String query = StringUtils.trimToNull(req.getParameter(SearchServlet.PARAM_QUERY));
		final String prevIdParam = ServletCommon.readParamWithDefault(req, resp, PARAM_PREV_ID, null);
		final String nextIdParam = ServletCommon.readParamWithDefault(req, resp, PARAM_NEXT_ID, null);
		final List<ContentItem> results;
		final Integer searchOffset;
		if (query != null) {
			if (this.mediaDb == null) return "";

			final Integer prevOffsetParam = ServletCommon.readIntParamWithDefault(req, resp, PARAM_PREV_OFFSET, null, i -> i >= 0);
			final Integer nextOffsetParam = ServletCommon.readIntParamWithDefault(req, resp, PARAM_NEXT_OFFSET, null, i -> i >= 0);
			if ((prevIdParam != null && prevOffsetParam != null) || (nextIdParam != null && nextOffsetParam != null)) {
				return printPrevNextLinksHtml(w, query, null, prevIdParam, nextIdParam, prevOffsetParam, nextOffsetParam);
			}

			final Integer offsetParam = ServletCommon.readIntParamWithDefault(req, resp, SearchServlet.PARAM_PAGE_OFFSET, 0, i -> i >= 0);
			if (offsetParam == null) return "";

			searchOffset = Math.max(offsetParam - PREV_NEXT_SEARCH_DISTANCE, 0);
			final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);
			final List<String> ids;
			try {
				ids = DbSearchParser.parseSearch(query, authIds).execute(this.mediaDb, PREV_NEXT_SEARCH_DISTANCE * 2, searchOffset);
			}
			catch (final SQLException e) {
				w.println("<p>Failed to make prev/next links: " + StringEscapeUtils.escapeHtml4(e.toString()) + "</p>");
				return "";
			}
			results = this.contentTree.getItemsForIds(ids, username);
		}
		else {
			if (prevIdParam != null || nextIdParam != null) {
				return printPrevNextLinksHtml(w, null, node, prevIdParam, nextIdParam, null, null);
			}

			searchOffset = null;
			results = node.getCopyOfItems();
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

		return printPrevNextLinksHtml(w, query, node, prevId, nextId, prevOffset, nextOffset);
	}

	/**
	 * return always starts with '?'.
	 */
	private static String printPrevNextLinksHtml(
			final PrintWriter w,
			final String query,
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
			allPath = "../" + node.getId();
			allTitle = StringEscapeUtils.escapeHtml4(node.getTitle());
			linkQuery = "?" + PARAM_NODE_ID + "=" + node.getId();
		}

		w.println("<div style=\"margin: 1em; display: flex; justify-content: space-between;\">");

		if (prevId != null) {
			w.print("<a id=\"previous\" href=\"");
			w.print(prevId);
			if (linkQuery != null) w.print(linkQuery);
			if (prevOffset != null) w.print(prevOffset);
			w.println("\">&lt;= Previous</a>");

			editReqQueryParms.append("&").append(PARAM_PREV_ID).append("=").append(prevId);
			if (prevOffset != null) editReqQueryParms.append("&").append(PARAM_PREV_OFFSET).append("=").append(prevOffset);
		}
		else {
			w.println("<span></span>");
		}

		w.print("<a id=\"up\" href=\"");
		w.print(allPath);
		w.println("\">" + allTitle + "</a>");

		if (nextId != null) {
			w.print("<a id=\"next\" href=\"");
			w.print(nextId);
			if (linkQuery != null) w.print(linkQuery);
			if (nextOffset != null) w.print(nextOffset);
			w.println("\">Next =&gt;</a>");

			editReqQueryParms.append("&").append(PARAM_NEXT_ID).append("=").append(nextId);
			if (nextOffset != null) editReqQueryParms.append("&").append(PARAM_NEXT_OFFSET).append("=").append(nextOffset);
		}
		else {
			w.println("<span></span>");
		}

		w.println("</div>");

		return editReqQueryParms.toString();
	}

	private void printSimpleTags(final ContentItem item, final PrintWriter w) throws SQLException {
		final Collection<String> tags = this.mediaDb.getTagsSimple(item.getId());
		if (tags.size() < 1) return;
		this.servletCommon.printRowOfTagsSimple(w, "../", tags);
		w.println("</br>");
	}

	private void printEditTags(final ContentItem item, final PrintWriter w) throws SQLException {
		final Collection<Tag> tags = this.mediaDb.getTags(item.getId(), false);
		w.println("<form action=\"\" method=\"POST\">");
		w.println("<input type=\"hidden\" name=\"action\" value=\"rmtags\">");
		for (final Tag tag : tags) {
			final Encoder encoder = Base64.getUrlEncoder().withoutPadding();
			final String b64tag = encoder.encodeToString(tag.getTag().getBytes(StandardCharsets.UTF_8))
					+ ":" + encoder.encodeToString(tag.getCls().getBytes(StandardCharsets.UTF_8));
			w.print("<input type=\"checkbox\" name=\"b64tag\"  style=\"margin: 0.5em;\" value=\"");
			w.print(b64tag);
			w.print("\" id=\"");
			w.print(b64tag);
			w.print("\">");
			w.print("<label style=\"margin: 0.5em;\" for=\"");
			w.print(b64tag);
			w.print("\">");
			w.print(StringEscapeUtils.escapeHtml4(tag.getTag()));
			if (tag.getCls().length() > 0) {
				w.print(" (");
				w.print(StringEscapeUtils.escapeHtml4(tag.getCls()));
				w.print(")");
			}
			w.println("</label><br>");
		}
		w.println("<input type=\"submit\" value=\"Delete\"  style=\"margin: 0.5em;\">");
		w.println("</form>");
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
		if (node == null || !node.isUserAuth(username)) {
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

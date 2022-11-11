package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.fourthline.cling.model.ModelUtil;

import com.google.common.net.UrlEscapers;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.TagFrequency;
import com.vaguehope.dlnatoad.db.search.DbSearchSyntax;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentItem.Order;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentServingHistory;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;
import com.vaguehope.dlnatoad.util.IndexAnd;
import com.vaguehope.dlnatoad.util.StringHelper;

public class ServletCommon {

	private final ContentTree contentTree;
	private final ImageResizer imageResizer;
	private final String hostName;
	private final ContentServingHistory contentServingHistory;
	private final boolean mediaDbEnabled;

	public ServletCommon(
			final ContentTree contentTree,
			final ImageResizer imageResizer,
			final String hostName,
			final ContentServingHistory contentServingHistory,
			final boolean mediaDbEnabled) {
		this.contentTree = contentTree;
		this.imageResizer = imageResizer;
		this.hostName = hostName;
		this.contentServingHistory = contentServingHistory;
		this.mediaDbEnabled = mediaDbEnabled;
	}

	public static void returnStatus (final HttpServletResponse resp, final int status, final String msg) throws IOException {
		resp.reset();
		returnStatusWithoutReset(resp, status, msg);
	}

	@SuppressWarnings("resource")
	public static void returnStatusWithoutReset (final HttpServletResponse resp, final int status, final String msg) throws IOException {
		resp.setContentType("text/plain");
		resp.setStatus(status);
		resp.getWriter().println(msg);
	}

	public static void returnDenied(final HttpServletResponse resp, final String username) throws IOException {
		if (username == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
		}
		else {
			returnForbidden(resp);
		}
	}

	public static void returnForbidden(final HttpServletResponse resp) throws IOException {
		ServletCommon.returnStatus(resp, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
	}

	public static void setHtmlContentType(final HttpServletResponse resp) {
		resp.setContentType("text/html; charset=utf-8");
	}

	public void headerAndStartBody(final PrintWriter w, final String title) {
		headerAndStartBody(w, "", title);
	}

	public void headerAndStartBody(final PrintWriter w, final String pathPrefix, final String title, final String... extraHeaderLines) {
		w.println("<!DOCTYPE html>");
		w.println("<html>");
		w.println("<head>");
		w.println("<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\">");

		w.print("<title>");
		if (StringUtils.isNotBlank(title)) {
			w.print(title);
			w.print(" - ");
		}
		w.print(C.METADATA_MODEL_NAME);
		w.print(" (");
		w.print(this.hostName);
		w.print(")");
		w.println("</title>");

		w.println("<meta name=\"viewport\" content=\"width=device-width, minimum-scale=1.0\">");

		if (this.mediaDbEnabled) {
			w.print("<link rel=\"stylesheet\" href=\"");
			w.print(pathPrefix);
			w.println("w/autocomplete.css\">");
			w.println("<script src=\"https://cdn.jsdelivr.net/npm/@tarekraafat/autocomplete.js@10.2.7/dist/autoComplete.min.js\"></script>");
		}
		for (final String line : extraHeaderLines) {
			w.println(line);
		}

		w.print("<link rel=\"stylesheet\" href=\"");
		w.print(pathPrefix);
		w.println("w/layout.css\">");
		// After the autocomplete css file for priority reasons.
		w.print("<link rel=\"stylesheet\" href=\"");
		w.print(pathPrefix);
		w.println("w/colours.css\">");

		w.println("<style>");
		w.println("body, div, input, label, p, span {font-family: sans-serif;}");
		w.println("a:link, a:visited {text-decoration: none;}");
		w.println("</style>");

		w.println("</head>");
		w.println("<body>");
	}

	public void endBody(final PrintWriter w) {
		w.println("</body></html>");
	}

	public void printLinkRow(final HttpServletRequest req, final PrintWriter w) {
		printLinkRow(req, w, "");
	}

	public void printLinkRow(final HttpServletRequest req, final PrintWriter w, final String pathPrefix) {
		w.println("<div>");

		w.print("<a href=\"");
		w.print(pathPrefix);
		w.println("./\">Home</a>");

		final String username = ReqAttr.USERNAME.get(req);
		if (username != null) {
			w.print("<span>[ ");
			w.print(username);
			w.println(" ]</span>");
		}
		else {
			w.println("<form style=\"display:inline;\" action=\"\" method=\"GET\">");
			w.println("<input type=\"hidden\" name=\"action\" value=\"login\">");
			w.println("<input type=\"submit\" value=\"Login\">");
			w.println("</form>");
		}

		final String query = StringUtils.trimToEmpty(req.getParameter(SearchServlet.PARAM_QUERY));
		w.print("<form style=\"display:inline-block;\" action=\"");
		w.print(pathPrefix);
		w.println("search\" method=\"GET\">");
		w.println("<div class=\"autocomplete_wrapper search_wrapper\">");
		w.print("<input type=\"text\" id=\"search\" name=\"query\" value=\"");
		w.print(StringEscapeUtils.escapeHtml4(query));
		w.println("\" style=\"width: 20em;\" autocomplete=\"off\" spellcheck=false autocorrect=\"off\" autocapitalize=\"off\">");
		w.println("</div>");

		if (ReqAttr.ALLOW_REMOTE_SEARCH.get(req)) {
			final String remote = StringUtils.trimToEmpty(req.getParameter(SearchServlet.PARAM_REMOTE));
			final String remoteChecked = StringUtils.isNotBlank(remote) ? "checked" : "";
			w.println("<span style=\"display:inline-block;\">");
			w.print("<input type=\"checkbox\" id=\"remote\" name=\"remote\" value=\"true\" ");
			w.print(remoteChecked);
			w.println(">");
			w.println("<label for=\"remote\">remote</label>");
			w.println("</span>");
		}
		w.println("<input type=\"submit\" value=\"Search\">");
		w.println("</form>");
		if (this.mediaDbEnabled) {
			w.print("<script src=\"");
			w.print(pathPrefix);
			w.println("w/autocomplete-search.js\"></script>");
		}

		w.println("</div>");
	}

	public void printNodeSubNodesAndItems(final PrintWriter w, final ContentNode contentNode, final List<ContentNode> nodesUserHasAuth, final Order sort) throws IOException {
		w.println("<ul>");
		final boolean[] autofocus = new boolean[] { true };
		for (final ContentNode node : nodesUserHasAuth) {
			appendDirectory(w, node, autofocus);
		}
		final List<ContentItem> imagesToThumb = new ArrayList<>();
		if (sort != null) {
			final List<ContentItem> items = contentNode.getCopyOfItems();
			items.sort(sort);
			for (final ContentItem i : items) {
				appendItemOrGetImageToThumb(w, i, autofocus, imagesToThumb);
			}
		}
		else {
			contentNode.withEachItem(i -> appendItemOrGetImageToThumb(w, i, autofocus, imagesToThumb));
		}

		w.println("</ul>");
		appendImageThumbnails(w, contentNode, imagesToThumb, autofocus);
	}

	private void appendItemOrGetImageToThumb(final PrintWriter w, final ContentItem item, final boolean[] autofocus, final List<ContentItem> imagesToThumb) throws IOException {
		if (this.imageResizer != null && item.getFormat().getContentGroup() == ContentGroup.IMAGE) {
			imagesToThumb.add(item);
		}
		else {
			appendItem(w, item, autofocus);
		}
	}

	public void printItemsAndImages(final PrintWriter w, final List<ContentItem> items, final Function<IndexAnd<ContentItem>, String> linkQuery) throws IOException {
		w.print("<h3>Local items: ");
		w.print(items.size());
		w.println("</h3><ul>");

		final boolean[] autofocus = new boolean[] { true };
		final List<IndexAnd<ContentItem>> imagesToThumb = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			final ContentItem item = items.get(i);
			if (this.imageResizer != null && item.getFormat().getContentGroup() == ContentGroup.IMAGE) {
				imagesToThumb.add(new IndexAnd<>(i, item));
			}
			else {
				appendItem(w, item, autofocus);
			}
		}
		w.println("</ul>");

		for (final IndexAnd<ContentItem> indexAnd : imagesToThumb) {
			appendImageThumbnail(w, indexAnd.getItem(), linkQuery.apply(indexAnd), autofocus);
		}
	}

	private static void appendDirectory(final PrintWriter w, final ContentNode node, final boolean[] autofocus) {
		w.print("<li><a href=\"");
		w.print(node.getId());
		w.print("\"");
		maybeSetAutofocus(w, autofocus);
		w.print(">");
		w.print(StringEscapeUtils.escapeHtml4(node.getTitle()));
		w.println("</a></li>");
	}

	private static void appendItem(final PrintWriter w, final ContentItem item, final boolean[] autofocus) throws IOException {
		w.print("<li><a href=\"");
		w.print(C.CONTENT_PATH_PREFIX);
		w.print(item.getId());
		w.print(".");
		w.print(item.getFormat().getExt());
		w.print("\"");
		maybeSetAutofocus(w, autofocus);
		w.print(">");
		w.print(item.getFile().getName());
		w.print("</a> [<a href=\"");
		w.print(C.CONTENT_PATH_PREFIX);
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

	private static void appendImageThumbnails(
			final PrintWriter w,
			final ContentNode node,
			final List<ContentItem> imagesToThumb,
			final boolean[] autofocus) throws IOException {
		final String linkQuery = "?" + ItemServlet.PARAM_NODE_ID + "=" + node.getId();
		for (final ContentItem item : imagesToThumb) {
			appendImageThumbnail(w, item, linkQuery, autofocus);
		}
	}

	private static void appendImageThumbnail(
			final PrintWriter w,
			final ContentItem item,
			final String linkQuery,
			final boolean[] autofocus) throws IOException {
		w.print("<span class=\"thumbnail\"><a href=\"");
		w.print(C.ITEM_PATH_PREFIX);
		w.print(item.getId());
		if (linkQuery != null) w.print(linkQuery);
		w.print("\"");
		maybeSetAutofocus(w, autofocus);
		w.print(">");
		w.print("<img src=\"");
		w.print(C.THUMBS_PATH_PREFIX);
		w.print(item.getId());
		w.print("\" title=\"");
		w.print(StringEscapeUtils.escapeHtml4(item.getTitle()));
		w.print("\">");
		w.println("</a></span>");
	}

	private static void maybeSetAutofocus(final PrintWriter w, final boolean[] autofocus) {
		if (autofocus[0]) {
			w.print(" autofocus");
			autofocus[0] = false;
		}
	}

	public void printRowOfTags(final PrintWriter w, final String pathPrefix, final List<TagFrequency> tags) {
		for (final TagFrequency t : tags) {
			printRowTag(w, pathPrefix, t.getTag(), t.getCount());
		}
	}

	public void printRowOfTags(final PrintWriter w, final String pathPrefix, final Collection<Tag> tags) {
		for (final Tag t : tags) {
			printRowTag(w, pathPrefix, t.getTag(), 0);
		}
	}

	public void printRowOfTagsSimple(final PrintWriter w, final String pathPrefix, final Collection<String> tags) {
		for (final String t : tags) {
			printRowTag(w, pathPrefix, t, 0);
		}
	}

	private static void printRowTag(final PrintWriter w, final String pathPrefix, final String tag, final int count) {
		w.print("<a style=\"padding-right: 0.5em;\" href=\"");
		w.print(pathPrefix);
		w.print("search?query=");
		w.print(StringEscapeUtils.escapeHtml4(
				UrlEscapers.urlFormParameterEscaper().escape(
						DbSearchSyntax.makeSingleTagSearch(tag))));
		w.print("\">");
		w.print(StringEscapeUtils.escapeHtml4(tag));
		if (count > 0) {
			w.print(" (");
			w.print(count);
			w.print(")");
		}
		w.println("</a>");
	}

	public void appendDebugFooter(final HttpServletRequest req, final PrintWriter w, final String pathPrefix) {
		w.println("<p>");

		w.print(this.contentServingHistory.getActiveCount());
		w.print(" active playbacks, ");
		w.print(this.contentServingHistory.getRecentlyActiveCount(TimeUnit.MINUTES.toSeconds(15)));
		w.print(" active in last 15 minutes.");
		w.println("</br>");

		w.print("content: ");
		w.print(this.contentTree.getNodeCount());
		w.print(" nodes, ");
		w.print(this.contentTree.getItemCount());
		w.println(" items.</br>");

		w.println("</p>");

		if (ReqAttr.ALLOW_UPNP_INSPECTOR.get(req)) {
			w.print("<a href=\"");
			w.print(pathPrefix);
			w.println("upnp\">UPNP</a>");
		}
	}

	private final static Set<String> ROOT_PATHS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"/",
			"/" + C.REVERSE_PROXY_PATH,
			"/" + C.REVERSE_PROXY_PATH + "/"
			)));

	public static String idFromPath(final String pathInfo, final String defVal) {
		if (pathInfo == null || pathInfo.length() < 1 || ROOT_PATHS.contains(pathInfo)) {
			return defVal;
		}

		String id = StringHelper.removePrefix(pathInfo, "/");
		id = StringHelper.removeSuffix(id, "/");
		// Remove everything before the last slash.
		final int lastSlash = id.lastIndexOf("/");
		if (lastSlash >= 0 && lastSlash < id.length() - 1) {
			id = id.substring(lastSlash + 1);
		}
		// Remove everything after first dot.
		final int firstDot = id.indexOf('.');
		if (firstDot > 0) {
			id = id.substring(0, firstDot);
		}
		return id;
	}

	public static Cookie findCookie(final HttpServletRequest req, final String name) {
		final Cookie[] cookies = req.getCookies();
		if (cookies == null) return null;

		for (final Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) return cookie;
		}

		return null;
	}

	public static String readRequiredParam(final HttpServletRequest req, final HttpServletResponse resp, final String param, final int minLength) throws IOException {
		final String[] vals = req.getParameterValues(param);
		if (vals != null && vals.length > 1) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param has multiple values: " + param);
			return null;
		}
		final String p = vals != null ? vals[0].trim() : null;
		if (p == null || p.length() < minLength) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param missing: " + param);
			return null;
		}
		return p;
	}

	public static String[] readRequiredParams(final HttpServletRequest req, final HttpServletResponse resp, final String param, final int minLength) throws IOException {
		final String[] vals = req.getParameterValues(param);
		if (vals == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param missing: " + param);
			return null;
		}
		for (final String val : vals) {
			if (val.length() < minLength) {
				ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param invalid value: " + param);
				return null;
			}
		}
		return vals;
	}

	public static String readParamWithDefault(final HttpServletRequest req, final HttpServletResponse resp,
			final String param, final String defVal) throws IOException {
		final String[] vals = req.getParameterValues(param);
		if (vals != null && vals.length > 1) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param has multiple values: " + param);
			return null;
		}
		final String p = vals != null ? StringUtils.trimToNull(vals[0]) : null;
		if (p == null) return defVal;
		return p;
	}

	public static Integer readIntParamWithDefault(final HttpServletRequest req, final HttpServletResponse resp,
			final String param, final Integer defVal,
			final Function<Integer, Boolean> validator) throws IOException {
		final String[] vals = req.getParameterValues(param);
		if (vals != null && vals.length > 1) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param has multiple values: " + param);
			return null;
		}
		final String p = vals != null ? StringUtils.trimToNull(vals[0]) : null;
		if (p == null) return defVal;
		final int i;
		try {
			i = Integer.parseInt(p);
		}
		catch (final NumberFormatException e) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param not a number: " + param);
			return null;
		}
		if (!validator.apply(i)) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param not valid: " + param);
			return null;
		}
		return i;
	}

	public static String query(final HttpServletRequest req) {
		final String q = req.getQueryString();
		if (q == null) return "";
		return "?" + q;
	}

	public static String queryWithParam(final HttpServletRequest req, final String name, final String value) {
		return queryWithParam(req, name + "=" + UrlEscapers.urlFormParameterEscaper().escape(value));
	}

	public static String queryWithParam(final HttpServletRequest req, final String nameAndValue) {
		final String q = req.getQueryString();
		if (q == null) {
			return "?" + nameAndValue;
		}
		return "?" + q + "&" + nameAndValue;
	}

}

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentServingHistory;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;
import com.vaguehope.dlnatoad.util.StringHelper;

public class ServletCommon {

	private final ContentTree contentTree;
	private final ImageResizer imageResizer;
	private final String hostName;
	private final ContentServingHistory contentServingHistory;

	public ServletCommon(
			final ContentTree contentTree,
			final ImageResizer imageResizer,
			final String hostName,
			final ContentServingHistory contentServingHistory,
			final ExecutorService exSvc) {
		this.contentTree = contentTree;
		this.imageResizer = imageResizer;
		this.hostName = hostName;
		this.contentServingHistory = contentServingHistory;
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

	public void headerAndStartBody(final PrintWriter w, final String title, final String... extraHeaderLines) {
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
		for (final String line : extraHeaderLines) {
			w.println(line);
		}

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
		final String username = ReqAttr.USERNAME.get(req);

		w.println("<a href=\"/\">Home</a>");

		if (ReqAttr.ALLOW_UPNP_INSPECTOR.get(req)) {
			w.print("<a href=\"");
			w.print(pathPrefix);
			w.println("upnp\">UPNP</a>");
		}

		final String query = StringUtils.trimToEmpty(req.getParameter(SearchServlet.PARAM_QUERY));
		w.print("<form style=\"display:inline;\" action=\"");
		w.print(pathPrefix);
		w.println("search\" method=\"GET\">");
		w.print("<input type=\"text\" id=\"query\" name=\"query\" value=\"");
		w.print(StringEscapeUtils.escapeHtml4(query));
		w.println("\">");
		if (ReqAttr.ALLOW_REMOTE_SEARCH.get(req)) {
			final String remote = StringUtils.trimToEmpty(req.getParameter(SearchServlet.PARAM_REMOTE));
			final String remoteChecked = StringUtils.isNotBlank(remote) ? "checked" : "";
			w.print("<input type=\"checkbox\" id=\"remote\" name=\"remote\" value=\"true\" ");
			w.print(remoteChecked);
			w.println(">");
			w.println("<label for=\"remote\">remote</label>");
		}
		w.println("<input type=\"submit\" value=\"Search\">");
		w.println("</form>");

		if (username != null) {
			w.print("<span>Username: ");
			w.print(username);
			w.println("</span>");
		}
		else {
			w.println("<form style=\"display:inline;\" action=\"\" method=\"GET\">");
			w.println("<input type=\"hidden\" name=\"action\" value=\"login\">");
			w.println("<input type=\"submit\" value=\"Login\">");
			w.println("</form>");
		}
	}

	public void printDirectoriesAndItems(final PrintWriter w, final ContentNode contentNode, final String username) throws IOException {
		w.print("<h3>");
		w.print(contentNode.getTitle());
		w.print(" (");
		w.print(contentNode.getNodeCount());
		w.print(" dirs, ");
		w.print(contentNode.getItemCount());
		w.println(" items)</h3><ul>");

		final boolean[] autofocus = new boolean[] { true };
		contentNode.withEachNode(c -> appendDirectory(w, c, username, autofocus));
		final List<ContentItem> imagesToThumb = new ArrayList<>();
		contentNode.withEachItem(i -> appendItemOrGetImageToThumb(w, i, autofocus, imagesToThumb));
		appendImageThumbnails(w, imagesToThumb, autofocus);

		w.println("</ul>");
	}

	public void printItemsAndImages(final PrintWriter w, final List<ContentItem> items) throws IOException {
		w.print("<h3>Local items: ");
		w.print(items.size());
		w.println("</h3><ul>");

		final boolean[] autofocus = new boolean[] { true };
		final List<ContentItem> imagesToThumb = new ArrayList<>();
		for (final ContentItem item : items) {
			appendItemOrGetImageToThumb(w, item, autofocus, imagesToThumb);
		}
		appendImageThumbnails(w, imagesToThumb, autofocus);

		w.println("</ul>");
	}

	private void appendItemOrGetImageToThumb(final PrintWriter w, final ContentItem item, final boolean[] autofocus, final List<ContentItem> imagesToThumb) throws IOException {
		if (this.imageResizer != null && item.getFormat().getContentGroup() == ContentGroup.IMAGE) {
			imagesToThumb.add(item);
		}
		else {
			appendItem(w, item, autofocus);
		}
	}

	private static void appendDirectory(final PrintWriter w, final ContentNode node, final String username, final boolean[] autofocus) {
		if (!node.isUserAuth(username)) return;

		w.print("<li><a href=\"");
		w.print(node.getId());
		w.print("\"");
		maybeSetAutofocus(w, autofocus);
		w.print(">");
		w.print(node.getTitle());
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

	private static void appendImageThumbnails(final PrintWriter w, final List<ContentItem> imagesToThumb, final boolean[] autofocus) throws IOException {
		for (final ContentItem item : imagesToThumb) {
			w.print("<span><a href=\"");
			w.print(C.ITEM_PATH_PREFIX);
			w.print(item.getId());
			w.print("\"");
			maybeSetAutofocus(w, autofocus);
			w.print(">");
			w.print("<img style=\"max-width: 6em; max-height: 5em; margin: 0.5em 0.5em 0 0.5em;\" src=\"");
			w.print(C.THUMBS_PATH_PREFIX);
			w.print(item.getId());
			w.print("\" title=\"");
			w.print(item.getTitle());
			w.print("\">");
			w.println("</a></span>");
		}
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

	public void appendDebugFooter(final PrintWriter w) {
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

}

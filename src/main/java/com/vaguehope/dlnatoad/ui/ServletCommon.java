package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.media.ContentServingHistory;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.ui.templates.PageScope;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.StringHelper;

public class ServletCommon {

	private final ContentTree contentTree;
	private final String hostName;
	private final ContentServingHistory contentServingHistory;
	private final boolean mediaDbEnabled;

	public ServletCommon(
			final ContentTree contentTree,
			final String hostName,
			final ContentServingHistory contentServingHistory,
			final boolean mediaDbEnabled) {
		this.contentTree = contentTree;
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

	public PageScope pageScope(final HttpServletRequest req, final String title, final String pathPrefix) {
		return new PageScope(
				pageTitle(title),
				pathPrefix,
				ReqAttr.USERNAME.get(req),
				this.mediaDbEnabled,
				StringUtils.trimToEmpty(req.getParameter(SearchServlet.PARAM_QUERY)),
				ReqAttr.ALLOW_REMOTE_SEARCH.get(req));
	}

	private String pageTitle(final String title) {
		String ret = "";
		if (StringUtils.isNotBlank(title)) {
			ret = title + " - ";
		}
		ret += C.METADATA_MODEL_NAME + " (" + this.hostName + ")";
		return ret;
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
		w.print(pageTitle(title));
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

	public String debugFooter() {
		final StringBuilder s = new StringBuilder();

		s.append("active playbacks: ");
		s.append(this.contentServingHistory.getActiveCount());
		s.append(", ");
		s.append(this.contentServingHistory.getRecentlyActiveCount(TimeUnit.MINUTES.toSeconds(15)));
		s.append(" in last 15 minutes.");
		s.append("\n");

		s.append("content: ");
		s.append(this.contentTree.getNodeCount());
		s.append(" nodes, ");
		s.append(this.contentTree.getItemCount());
		s.append(" items.\n");

		final long totalMemory = Runtime.getRuntime().totalMemory();
		final long usedMemory = totalMemory - Runtime.getRuntime().freeMemory();
		s.append("heap: ");
		s.append(FileHelper.readableFileSize(usedMemory));
		s.append(" of ");
		s.append(FileHelper.readableFileSize(totalMemory));
		s.append(" (max ");
		s.append(FileHelper.readableFileSize(Runtime.getRuntime().maxMemory()));
		s.append(").\n");

		return s.toString();
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

	public static String queryWithParam(final HttpServletRequest req, final String nameAndValue) {
		final String q = req.getQueryString();
		if (q == null) {
			return "?" + nameAndValue;
		}
		return "?" + q + "&" + nameAndValue;
	}

}

package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.resolver.ClasspathResolver;
import com.github.mustachejava.resolver.FileSystemResolver;
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
	private final File templateRoot;

	private final MustacheFactory defaultMustacheFactory = new DefaultMustacheFactory(new ClasspathResolver("templates"));

	public ServletCommon(
			final ContentTree contentTree,
			final String hostName,
			final ContentServingHistory contentServingHistory,
			final boolean mediaDbEnabled,
			final File templateRoot) {
		this.contentTree = contentTree;
		this.hostName = hostName;
		this.contentServingHistory = contentServingHistory;
		this.mediaDbEnabled = mediaDbEnabled;
		this.templateRoot = templateRoot;
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

	public Supplier<Mustache> mustacheTemplate(final String name) {
		if (this.templateRoot == null) {
			final Mustache compiled = this.defaultMustacheFactory.compile(name);
			return () -> compiled;
		}

		// Bypass caching when dev testing.
		return () -> {
			final MustacheFactory mf = new DefaultMustacheFactory(new FileSystemResolver(this.templateRoot));
			return mf.compile(name);
		};
	}

	public PageScope pageScope(final HttpServletRequest req, final String title, final String pathPrefix) {
		return pageScope(req, title, pathPrefix, null);
	}

	public PageScope pageScope(final HttpServletRequest req, final String title, final String pathPrefix, final String query) {
		return new PageScope(
				pageTitle(title),
				pathPrefix,
				ReqAttr.USERNAME.get(req),
				this.mediaDbEnabled,
				StringUtils.trimToEmpty(query),
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

	public String debugFooter() {
		final StringBuilder s = new StringBuilder();

		s.append("active: ");
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

	private static String removeReverseProxyPrefix(final String pathInfo) {
		if (pathInfo == null || pathInfo.length() < 1 || !pathInfo.startsWith("/")) {
			return null;
		}
		return StringHelper.removePrefix(pathInfo, "/" + C.REVERSE_PROXY_PATH);
	}

	public static String fileFromPath(final String pathInfo) {
		String p = removeReverseProxyPrefix(pathInfo);
		if (p == null) return null;
		if (p == "/") return null;

		p = StringHelper.removePrefix(p, "/");
		return p;
	}

	public static String firstDirFromPath(final String pathInfo) {
		final String p = removeReverseProxyPrefix(pathInfo);
		if (p == null) return null;

		if (p.length() < 3) return null;
		if (!p.startsWith("/")) return null;
		final int x = p.indexOf('/', 2);
		return p.substring(1, x);
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

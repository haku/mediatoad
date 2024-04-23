package com.vaguehope.dlnatoad.ui;

import java.io.PrintWriter;
import java.util.Date;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.text.StringEscapeUtils;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.util.StringHelper;
import com.vaguehope.dlnatoad.util.ThreadSafeDateFormatter;

public class Webdav {

	private static final ThreadSafeDateFormatter RFC1123_DATE = new ThreadSafeDateFormatter("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

	public static void appendPropfindNode(final HttpServletRequest req, final String username, final PrintWriter w, final ContentNode node, final boolean appendIdToPath) {
		if (!node.isUserAuth(username)) return;

		String path = req.getPathInfo();
		if (appendIdToPath) {
			path = StringHelper.removeSuffix(path, "/") + "/" + node.getId();
		}

		appendPropfindDir(w, path, node.getTitle(), node.getLastModified());
	}

	public static void appendPropfindDir(final PrintWriter w, String path, String title, final long lastModified) {
		w.println("<D:response>");
		w.println("<D:href>" + path + "</D:href>");
		w.println("<D:propstat>");
		w.println("<D:prop>");
		w.println("<D:resourcetype><D:collection/></D:resourcetype>");

		w.print("<D:displayname>");
		w.print(StringEscapeUtils.escapeXml11(title));
		w.println("</D:displayname>");

		if (lastModified > 0) {
			w.print("<D:getlastmodified>");
			w.print(RFC1123_DATE.get().format(new Date(lastModified)));
			w.print("</D:getlastmodified>");
		}

		w.println("</D:prop>");
		w.println("<D:status>HTTP/1.1 200 OK</D:status>");
		w.println("</D:propstat>");
		w.println("</D:response>");
	}

	public static void appendPropfindItem(final HttpServletRequest req, final PrintWriter w, final ContentItem item, final boolean appendIdToPath) {
		String path = req.getPathInfo();
		if (appendIdToPath) {
			path = StringHelper.removeSuffix(path, "/") + "/" + item.getId();
		}

		appendPropfindItem(w, item, path);
	}

	public static void appendPropfindItem(final PrintWriter w, final ContentItem item, final String path) {
		w.println("<D:response>");
		w.println("<D:href>" + path + "</D:href>");
		w.println("<D:propstat>");
		w.println("<D:prop>");
		w.println("<D:resourcetype/>");

		w.print("<D:displayname>");
		w.print(StringEscapeUtils.escapeXml11(item.getTitle()));
		w.println("</D:displayname>");

		if (item.getFormat() != null) {
			w.print("<D:getcontenttype>");
			w.print(item.getFormat().getMime());
			w.println("</D:getcontenttype>");
		}

		final long fileLength = item.getFileLength();
		if (fileLength > 0) {
			w.print("<D:getcontentlength>");
			w.print(fileLength);
			w.println("</D:getcontentlength>");
		}

		final long lastModified = item.getLastModified();
		if (lastModified > 0) {
			w.print("<D:getlastmodified>");
			w.print(RFC1123_DATE.get().format(new Date(lastModified)));
			w.print("</D:getlastmodified>");
		}

		w.println("</D:prop>");
		w.println("<D:status>HTTP/1.1 200 OK</D:status>");
		w.println("</D:propstat>");
		w.println("</D:response>");
	}

}

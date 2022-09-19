package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collection;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;

public class ItemServlet extends HttpServlet {

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

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
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

		final Collection<Tag> tags;
		if (this.mediaDb != null) {
			try {
				tags = this.mediaDb.getTags(item.getId(), false);
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
		}
		else {
			tags = Collections.emptyList();
		}

		ServletCommon.setHtmlContentType(resp);
		@SuppressWarnings("resource")
		final PrintWriter w = resp.getWriter();
		this.servletCommon.headerAndStartBody(w, item.getId());
		this.servletCommon.printLinkRow(req, w, "../");
		w.println("<br>");

		w.print("<h2>");
		w.print(item.getTitle());
		w.println("</h2>");
		w.println("<div>");
		w.println("<span style=\"padding-right: 0.5em;\">Tags:</span>");

		final boolean allowEditTags = ReqAttr.ALLOW_EDIT_TAGS.get(req);
		final boolean editMode = allowEditTags && "true".equalsIgnoreCase(req.getParameter("edit"));
		if (editMode) {
			w.println("<form action=\"\" method=\"POST\">");
			w.println("<input type=\"hidden\" name=\"action\" value=\"rmtags\">");
		}
		for (final Tag tag : tags) {
			if (editMode) {
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
			else {
				w.print("<span style=\"padding-right: 0.5em;\">");
				w.print(StringEscapeUtils.escapeHtml4(tag.getTag()));
				w.println("</span>");
			}
		}
		if (editMode) {
			w.println("<input type=\"submit\" value=\"Delete\"  style=\"margin: 0.5em;\">");
			w.println("</form>");
		}

		if (allowEditTags) {
			w.println("</br>");
			w.println("<div style=\"padding-top: 0.5em\">");
			w.println("<a href=\"?edit=true\">Edit</a>");
			w.println("<form style=\"display:inline;\" action=\"\" method=\"POST\">");
			w.println("<input type=\"hidden\" name=\"action\" value=\"addtag\">");
			w.println("<input type=\"text\" id=\"tag\" name=\"tag\" value=\"\">");
			w.println("<input type=\"submit\" value=\"Add\">");
			w.println("</form>");
			w.println("</div>");
		}

		w.println("</div>");

		w.print("<img style=\"max-width: 100%; max-height: 50em; padding-top: 1em;\" src=\"../");
		w.print(C.CONTENT_PATH_PREFIX);
		w.print(item.getId());
		w.print(".");
		w.print(item.getFormat().getExt());
		w.print("\">");

		this.servletCommon.endBody(w);
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
			final String tag = readRequiredParam(req, resp, "tag", 1);
			if (tag == null) return;
			try (final WritableMediaDb w = this.mediaDb.getWritable()) {
				w.addTag(item.getId(), tag, System.currentTimeMillis());
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
			LOG.info("{} added tag to {}: {}", username, item.getId(), tag);
			resp.addHeader("Location", item.getId());
			ServletCommon.returnStatusWithoutReset(resp, HttpServletResponse.SC_SEE_OTHER, "Tag added.");
		}
		else if ("rmtags".equalsIgnoreCase(req.getParameter("action"))) {
			final String[] b64tagsandclss = readRequiredParams(req, resp, "b64tag", 1);
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
			resp.addHeader("Location", item.getId());
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

	private static String readRequiredParam(final HttpServletRequest req, final HttpServletResponse resp, final String param, final int minLength) throws IOException {
		final String[] vals = req.getParameterValues(param);
		if (vals != null && vals.length > 1) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param has multiple values: " + param);
			return null;
		}
		final String p = vals != null ? vals[0] : null;
		if (p == null || p.length() < minLength) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Param missing: " + param);
			return null;
		}
		return p;
	}

	private static String[] readRequiredParams(final HttpServletRequest req, final HttpServletResponse resp, final String param, final int minLength) throws IOException {
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

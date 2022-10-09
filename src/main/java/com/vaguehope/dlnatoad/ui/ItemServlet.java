package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.FileData;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.util.FileHelper;

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

	@SuppressWarnings("resource")
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

		ServletCommon.setHtmlContentType(resp);
		final PrintWriter w = resp.getWriter();
		this.servletCommon.headerAndStartBody(w, item.getTitle());
		this.servletCommon.printLinkRow(req, w, "../");
		w.println("<br>");

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
				w.println("<div style=\"padding-top: 0.5em\">");
				w.println("<a href=\"?edit=true\">Edit</a>");
				w.println("<form style=\"display:inline;\" action=\"\" method=\"POST\">");
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

		w.print("<img style=\"max-width: 100%; max-height: 50em; padding-top: 1em;\" src=\"../");
		w.print(C.CONTENT_PATH_PREFIX);
		w.print(item.getId());
		w.print(".");
		w.print(item.getFormat().getExt());
		w.println("\">");
		w.println("<br>");

		w.print("<a href=\"../");
		w.print(C.CONTENT_PATH_PREFIX);
		w.print(item.getId());
		w.print(".");
		w.print(item.getFormat().getExt());
		w.print("\" download=\"");
		w.print(StringEscapeUtils.escapeHtml4(item.getFile().getName()));
		w.println("\">Download</a>");

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
			resp.addHeader("Location", item.getId() + "?autofocus=addtag");
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

}

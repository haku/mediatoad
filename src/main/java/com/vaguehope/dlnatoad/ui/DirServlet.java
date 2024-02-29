package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.fourthline.cling.model.ModelUtil;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.resolver.ClasspathResolver;
import com.google.common.net.UrlEscapers;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.DbCache;
import com.vaguehope.dlnatoad.db.TagFrequency;
import com.vaguehope.dlnatoad.db.search.DbSearchSyntax;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentItem.Order;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.ui.templates.NodeIndexScope;
import com.vaguehope.dlnatoad.ui.templates.PageScope;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class DirServlet extends HttpServlet {

	private static final long serialVersionUID = 6207424145390666199L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final ImageResizer imageResizer;
	private final DbCache dbCache;
	private final Mustache nodeIndexTemplate;

	public DirServlet(final ServletCommon servletCommon, final ContentTree contentTree, final ImageResizer imageResizer, final DbCache dbCache) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.imageResizer = imageResizer;
		this.dbCache = dbCache;

		final MustacheFactory mf = new DefaultMustacheFactory(new ClasspathResolver("templates"));
		this.nodeIndexTemplate = mf.compile("nodeindex.html");
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

	// https://github.com/spullara/mustache.java
	@SuppressWarnings("resource")
	private void returnNodeAsHtml(final HttpServletRequest req, final HttpServletResponse resp, final ContentNode node, final String username) throws IOException {
		final PageScope pageScope = this.servletCommon.pageScope(req, node.getTitle(), "../");
		final List<ContentNode> nodesUserHasAuth = node.nodesUserHasAuth(username);
		final String listTitle = makeIndexTitle(node, nodesUserHasAuth);
		final long nodeTotalFileLength = node.getTotalFileLength();

		final NodeIndexScope nodeIndexScope = new NodeIndexScope(
				listTitle,
				node.getId(),
				ContentGroup.ROOT.getId().equals(node.getId()) ? null : node.getParentId(),
				node.getFile() != null ? node.getFile().getName() : node.getTitle(),
				FileHelper.readableFileSize(nodeTotalFileLength));

		appendNodes(nodeIndexScope, nodesUserHasAuth);
		if (!appendItems(nodeIndexScope, node, req, resp)) return;  // false means error was written to resp.
		maybeAppendTopTags(nodeIndexScope, node, username);

		ServletCommon.setHtmlContentType(resp);
		this.nodeIndexTemplate.execute(resp.getWriter(), new Object[] { pageScope, nodeIndexScope }).flush();
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

	private static void appendNodes(final NodeIndexScope nodeIndexScope, final List<ContentNode> nodesUserHasAuth) {
		for (final ContentNode n : nodesUserHasAuth) {
			nodeIndexScope.addItem(C.DIR_PATH_PREFIX + n.getId(), n.getTitle());
		}
	}

	private boolean appendItems(
			final NodeIndexScope nodeIndexScope,
			final ContentNode node,
			final HttpServletRequest req,
			final HttpServletResponse resp) throws IOException {
		final String sortRaw = ServletCommon.readParamWithDefault(req, resp, "sort", "");
		if (sortRaw == null) return false;

		final Order sort;
		if ("modified".equalsIgnoreCase(sortRaw)) {
			sort = ContentItem.Order.MODIFIED_DESC;
		}
		else {
			sort = null;
		}

		final String linkQuery = "?" + ItemServlet.PARAM_NODE_ID + "=" + node.getId();

		if (sort != null) {
			final List<ContentItem> items = node.getCopyOfItems();
			items.sort(sort);
			for (final ContentItem i : items) {
				appendItem(nodeIndexScope, i, linkQuery);
			}
		}
		else {
			node.withEachItem(i -> appendItem(nodeIndexScope, i, linkQuery));
		}

		return true;
	}

	// TODO merge with SearchServlet.appendItem()
	private void appendItem(
			final NodeIndexScope nodeIndexScope,
			final ContentItem i,
			final String linkQuery) throws IOException {

		if (this.imageResizer != null && i.getFormat().getContentGroup() == ContentGroup.IMAGE) {
			nodeIndexScope.addThumb(
					C.ITEM_PATH_PREFIX + i.getId() + linkQuery,
					C.THUMBS_PATH_PREFIX + i.getId(),
					i.getTitle());
		}
		else {
			final long fileLength = i.getFileLength();
			final long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(i.getDurationMillis());
			nodeIndexScope.addItem(
					C.CONTENT_PATH_PREFIX + i.getId() + "." + i.getFormat().getExt(),
					i.getFile().getName(),
					fileLength > 0 ? FileHelper.readableFileSize(fileLength) : null,
					durationSeconds > 0 ? ModelUtil.toTimeString(durationSeconds) : null);
		}
	}

	private void maybeAppendTopTags(final NodeIndexScope nodeIndexScope, final ContentNode node, final String username) throws IOException {
		if (this.dbCache != null) {
			final File dir = node.getFile();
			final String pathPrefix = dir != null ? dir.getAbsolutePath() : null;
			if (pathPrefix == null && !ContentGroup.ROOT.getId().equals(node.getId())) return;

			final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);
			try {
				final List<TagFrequency> topTags = this.dbCache.getTopTags(authIds, pathPrefix);
				if (topTags.size() > 0) {
					for (final TagFrequency tag : topTags) {
						final String path = "search?query=" + StringEscapeUtils.escapeHtml4(
								UrlEscapers.urlFormParameterEscaper().escape(
										DbSearchSyntax.makeSingleTagSearch(tag.getTag())));
						nodeIndexScope.addTopTag(path, tag.getTag(), tag.getCount());
					}
				}
			}
			catch (final SQLException e) {
				throw new IOException(e);
			}
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

	private ContentNode getNodeFromPath(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final String id = ServletCommon.idFromPath(req.getPathInfo(), null);
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

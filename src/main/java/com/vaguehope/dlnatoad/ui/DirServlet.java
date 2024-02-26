package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.resolver.ClasspathResolver;
import com.google.common.collect.ImmutableMap;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentItem.Order;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;

public class DirServlet extends HttpServlet {

	private static final long serialVersionUID = 6207424145390666199L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final Mustache nodeIndexTemplate;

	public DirServlet(final ServletCommon servletCommon, final ContentTree contentTree) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;

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
	private void returnNodeAsHtml(final HttpServletRequest req, final HttpServletResponse resp, final ContentNode node, final String username) throws IOException {
		final Map<String, Object> scopes = this.servletCommon.baseTemplateScope(req, node.getTitle(), "../");
		final List<ContentNode> nodesUserHasAuth = node.nodesUserHasAuth(username);

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
		scopes.put("list_title", listTitle);

		final String sortRaw = ServletCommon.readParamWithDefault(req, resp, "sort", "");
		if (sortRaw == null) return;
		final Order sort;
		if ("modified".equalsIgnoreCase(sortRaw)) {
			sort = ContentItem.Order.MODIFIED_DESC;
		}
		else {
			sort = null;
		}

		// TODO use proper objects
		// TODO include thumbs
		// TODO include autofocus
		final List<Map<String, String>> listItems = new ArrayList<>();
		for (final ContentNode n : nodesUserHasAuth) {
			listItems.add(ImmutableMap.of("path", n.getId(), "title", n.getTitle()));
		}
		// TODO include duration for items
		// TODO include file size for items
		// TODO include download link for items
		final Function<ContentItem, Void> addItem = (i) -> {
			listItems.add(ImmutableMap.of(
					"path", C.CONTENT_PATH_PREFIX + i.getId() + "." + i.getFormat().getExt(),
					"title", i.getFile().getName()));
			return null;
		};
		if (sort != null) {
			final List<ContentItem> items = node.getCopyOfItems();
			items.sort(sort);
			for (final ContentItem i : items) {
				addItem.apply(i);
			}
		}
		else {
			node.withEachItem(i -> addItem.apply(i));
		}
		scopes.put("list_items", listItems);

		// TODO printTopTags(w, contentNode, username);

		nodeIndexTemplate.execute(resp.getWriter(), scopes).flush();
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

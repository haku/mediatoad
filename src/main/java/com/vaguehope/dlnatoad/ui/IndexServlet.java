package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.teleal.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;

public class IndexServlet extends HttpServlet {

	private static final long serialVersionUID = -8907271726001369264L;

	private final ContentTree contentTree;

	public IndexServlet (final ContentTree contentTree) {
		this.contentTree = contentTree;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String path = req.getPathInfo();
		if (path == null || path.length() < 1 || "/".equals(path)) {
			printIndex(req, resp);
		}
		else if (path.startsWith("/")) {
			printDir(resp, path);
		}
		else {
			returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + path);
		}
	}

	private void printIndex (final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final List<ContentNode> dirNodes = new ArrayList<ContentNode>();
		for (final ContentNode node : this.contentTree.getNodes()) {
			if (node.getFile() == null && !ContentGroup.incluesId(node.getId())) dirNodes.add(node);
		}
		Collections.sort(dirNodes, NodeNameSorter.INSTANCE);

		resp.setContentType("text/html");
		final PrintWriter w = resp.getWriter();
		w.println("<html><body>");
		for (final ContentNode node : dirNodes) {
			w.print("<p><a href=\"");
			w.print(req.getRequestURI());
			w.print("/");
			w.print(node.getId());
			w.print("\">");
			w.print(node.getContainer().getTitle());
			w.println("</a></p>");
		}
		w.println("</html></body>");
	}

	private void printDir (final HttpServletResponse resp, final String path) throws IOException {
		final String id = path.substring(1);
		final ContentNode dirNode = this.contentTree.getNode(id);
		if (dirNode == null) {
			returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown ID: " + id);
			return;
		}

		final List<ContentNode> itemNodes = new ArrayList<ContentNode>();
		for (final Item item : dirNode.getContainer().getItems()) {
			itemNodes.add(this.contentTree.getNode(item.getId()));
		}
		Collections.sort(itemNodes, NodeNameSorter.INSTANCE);

		resp.setContentType("text/html");
		final PrintWriter w = resp.getWriter();
		w.println("<html><body>");
		for (final ContentNode node : itemNodes) {
			w.print("<p><a href=\"/");
			w.print(node.getId());
			w.print("\" download=\"");
			w.print(node.getFile().getName());
			w.print("\">");
			w.print(node.getFile().getName());
			w.println("</a></p>");
		}
		w.println("</html></body>");
	}

	private enum NodeNameSorter implements Comparator<ContentNode> {
		INSTANCE;

		@Override
		public int compare (final ContentNode o1, final ContentNode o2) {
			if (o1.getFile() == null || o2.getFile() == null) return 0;
			return o1.getFile().getName().toLowerCase().compareTo(o2.getFile().getName().toLowerCase());
		}
	}

	private static void returnStatus (final HttpServletResponse resp, final int status, final String msg) throws IOException {
		resp.reset();
		resp.setContentType("text/plain");
		resp.setStatus(status);
		resp.getWriter().println(msg);
	}

}

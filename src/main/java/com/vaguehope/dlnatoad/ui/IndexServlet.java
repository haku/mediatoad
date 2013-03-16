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
		List<ContentNode> nodes = new ArrayList<ContentNode>(this.contentTree.getNodes());
		Collections.sort(nodes, new Comparator<ContentNode>() {
			@Override
			public int compare (final ContentNode o1, final ContentNode o2) {
				if (o1.getFile() == null || o2.getFile() == null) return 0;
				return o1.getFile().compareTo(o2.getFile());
			}
		});

		resp.setContentType("text/html");
		PrintWriter w = resp.getWriter();
		w.println("<html><body>");
		for (ContentNode node : nodes) {
			if (node.getFile() == null) continue;
			w.print("<p><a href=\"");
			w.print(node.getId());
			w.print("\">");
			w.print(node.getFile().getName());
			w.print("</a></p>");
		}
		w.println("</html></body>");
	}

}

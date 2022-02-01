package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.dlnaserver.SearchEngine;

public class SearchServlet extends HttpServlet {

	private static final int MAX_RESULTS = 500;
	private static final Logger LOG = LoggerFactory.getLogger(SearchServlet.class);
	private static final long serialVersionUID = -3882119061427383748L;

	private final ServletCommon servletCommon;
	private final SearchEngine searchEngine;
	private final ContentTree contentTree;

	public SearchServlet(final ServletCommon servletCommon, final ContentTree contentTree) {
		this(servletCommon, contentTree, new SearchEngine());
	}

	public SearchServlet(final ServletCommon servletCommon, final ContentTree contentTree, final SearchEngine searchEngine) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.searchEngine = searchEngine;
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		ServletCommon.setHtmlContentType(resp);
		@SuppressWarnings("resource")
		final PrintWriter w = resp.getWriter();
		this.servletCommon.headerAndStartBody(w, "Search");
		this.servletCommon.printLinkRow(req, w);

		final String query = StringUtils.trimToEmpty(req.getParameter("query"));
		if (!StringUtils.isBlank(query)) {
			final String searchCriteria = String.format("(dc:title contains \"%s\")", query);
			final ContentNode rootNode = this.contentTree.getNode(ContentGroup.ROOT.getId());
			try {
				final List<Item> results = this.searchEngine.search(rootNode, searchCriteria, MAX_RESULTS);
				this.servletCommon.printItemsAndImages(w, results);
			}
			catch (final ContentDirectoryException e) {
				LOG.warn(String.format("Failed to parse search request (query=%s).", query));
				w.print("<p>Failed to parse query: ");
				w.print(e.toString());
				w.println("</p>");
			}
		}

		this.servletCommon.endBody(w);
	}


}

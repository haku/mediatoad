package mediatoad.ui;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import mediatoad.media.ContentGroup;
import mediatoad.media.ContentNode;
import mediatoad.media.ContentServlet;
import mediatoad.media.ContentTree;

public class IndexServlet extends HttpServlet {

	private static final long serialVersionUID = -8907271726001369264L;

	private final ContentTree contentTree;
	private final ContentServlet contentServlet;
	private final DirServlet dirServlet;
	private final ServletCommon servletCommon;

	public IndexServlet(
			final ContentTree contentTree,
			final ContentServlet contentServlet,
			final DirServlet dirServlet,
			final ServletCommon servletCommon) {
		this.contentTree = contentTree;
		this.contentServlet = contentServlet;
		this.dirServlet = dirServlet;
		this.servletCommon = servletCommon;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String id = this.servletCommon.idFromPath(req.getPathInfo(), ContentGroup.ROOT.getId());
		final ContentNode contentNode = this.contentTree.getNode(id);
		// ContentServlet does extra parsing and Index only handles directories anyway.
		if (contentNode == null) {
			this.contentServlet.service(req, resp);
			return;
		}

		req.setAttribute(DirServlet.PROXIED_FROM_INDEX_ATTR, Boolean.TRUE);
		this.dirServlet.doGet(req, resp);
	}

}

package mediatoad.media;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.resource.Resource;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mediatoad.ui.ServletCommon;
import mediatoad.util.MyFileServlet;

public class ContentServlet extends MyFileServlet {

	private static final long serialVersionUID = -4819786280597656455L;

	private final ContentTree contentTree; // NOSONAR
	private final ContentServingHistory contentServingHistory;
	private final ServletCommon servletCommon;

	@SuppressFBWarnings(value = "EI_EXPOSE_REP2")
	public ContentServlet (final ContentTree contentTree, final ContentServingHistory contentServingHistory, final ServletCommon servletCommon) {
		super();
		this.contentTree = contentTree;
		this.contentServingHistory = contentServingHistory;
		this.servletCommon = servletCommon;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final ContentItem item = this.servletCommon.itemFromPathWithPermissionOrError(req, resp);
		if (item == null) return;

		final String remoteAddr = req.getRemoteAddr();
		final String requestURI = req.getRequestURI();
		try {
			this.contentServingHistory.recordStart(remoteAddr, requestURI);
			super.doGet(req, resp);
		}
		finally {
			this.contentServingHistory.recordEnd(remoteAddr, requestURI);
		}
	}

	@Override
	public Resource getResource (final String pathInContext) {
		if (pathInContext.endsWith(".gz")) return null;

		final String id = this.servletCommon.idFromPath(pathInContext, null);
		final ContentItem item = this.contentTree.getItem(id);
		if (item != null) {
			return Resource.newResource(item.getFile());
		}
		return null;
	}

}

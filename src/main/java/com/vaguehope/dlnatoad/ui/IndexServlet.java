package com.vaguehope.dlnatoad.ui;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentServingHistory;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MediaId;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class IndexServlet extends HttpServlet {

	private static final long serialVersionUID = -8907271726001369264L;

	private final ContentTree contentTree;
	private final MediaId mediaId;
	private final ImageResizer imageResizer;
	private final String hostName;
	private final ContentServingHistory contentServingHistory;

	public IndexServlet (final ContentTree contentTree, final MediaId mediaId, final ImageResizer imageResizer, final String hostName, final ContentServingHistory contentServingHistory) {
		this.contentTree = contentTree;
		this.mediaId = mediaId;
		this.imageResizer = imageResizer;
		this.hostName = hostName;
		this.contentServingHistory = contentServingHistory;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		String path = req.getPathInfo();
		if (path == null || path.length() < 1 || "/".equals(path)) {
			path = ContentGroup.ROOT.getId();
		}
		else if (path.startsWith("/")) {
			path = path.substring(1);
		}

		final ContentNode contentNode = this.contentTree.getNode(path);
		if (contentNode == null) {
			returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Not found: " + path);
			return;
		}
		printDir(resp, contentNode);
	}

	private void printDir (final HttpServletResponse resp, final ContentNode contentNode) throws IOException {
		final Container dirNodeContainer = contentNode.getContainer();
		if (dirNodeContainer == null) {
			returnStatus(resp, HttpServletResponse.SC_NOT_FOUND, "Item is a not a directory: " + contentNode.getId());
			return;
		}

		final List<Container> dirs = new ArrayList<Container>();
		final List<ContentNode> items = new ArrayList<ContentNode>();
		synchronized (dirNodeContainer) {
			for (final Container c : dirNodeContainer.getContainers()) {
				dirs.add(c);
			}
			for (final Item item : dirNodeContainer.getItems()) {
				items.add(this.contentTree.getNode(item.getId()));
			}
		}
		Collections.sort(items, NodeOrder.TITLE_OR_NAME);

		resp.setContentType("text/html; charset=utf-8");
		final PrintWriter w = resp.getWriter();

		w.println("<html>");
		w.println("<head>");
		w.println("<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\">");

		w.print("<title>");
		w.print(C.METADATA_MODEL_NAME);
		w.print(" (");
		w.print(this.hostName);
		w.print(")");
		w.println("</title>");

		w.println("<meta name=\"viewport\" content=\"width=device-width, minimum-scale=1.0, maximum-scale=1.0\">");
		w.println("</head>");
		w.println("<body><h3>");
		w.print(dirNodeContainer.getTitle());
		w.print(" (");
		w.print(dirs.size());
		w.println(" dirs, ");
		w.print(items.size());
		w.println(" items)</h3><ul>");

		for (final Container dir : dirs) {
			w.print("<li><a href=\"/index/");
			w.print(dir.getId());
			w.print("\">");
			w.print(dir.getTitle());
			w.println("</a></li>");
		}

		final List<ContentNode> imagesToThumb = new ArrayList<ContentNode>();

		for (final ContentNode node : items) {
			if (this.imageResizer != null && node.getFormat().getContentGroup() == ContentGroup.IMAGE) {
				imagesToThumb.add(node);
				continue;
			}

			w.print("<li><a href=\"/");
			w.print(node.getId());
			w.print(".");
			w.print(node.getFormat().getExt());
			w.print("\">");
			w.print(node.getFile().getName());
			w.print("</a> [<a href=\"/");
			w.print(node.getId());
			w.print(".");
			w.print(node.getFormat().getExt());
			w.print("\" download=\"");
			w.print(node.getFile().getName());
			w.print("\">");
			w.print(FileHelper.readableFileSize(node.getItem().getFirstResource().getSize()));
			w.print("</a>]");

			final List<Res> ress = node.getItem().getResources();
			if (ress != null) {
				for (final Res res : ress) {
					final String duration = res.getDuration();
					if (duration != null && duration.length() > 0) {
						w.print(" (");
						w.print(duration);
						w.print(")");
						break;
					}
				}
			}

			w.println("</li>");
		}

		w.println("</ul>");

		for (final ContentNode node : imagesToThumb) {
			final File thumbFile = this.imageResizer.resizeFile(node.getFile(), 200, 0.8f);
			final String thumbId = this.mediaId.contentIdSync(ContentGroup.THUMBNAIL, thumbFile);
			this.contentTree.addNode(new ContentNode(thumbId, null, thumbFile, MediaFormat.JPEG));

			w.print("<span><a href=\"/");
			w.print(node.getId());
			w.print(".");
			w.print(node.getFormat().getExt());
			w.print("\">");
			w.print("<img style=\"max-width: 6em; max-height: 5em; margin: 0.5em 0.5em 0 0.5em;\" src=\"/");
			w.print(thumbId);
			w.print("\">");
			w.println("</a></span>");
		}

		w.print("<p>");
		w.print(this.contentServingHistory.getActiveCount());
		w.print(" active, ");
		w.print(this.contentServingHistory.getRecentlyActiveCount(TimeUnit.MINUTES.toSeconds(15)));
		w.print(" active in last 15 minutes.");
		w.println("</p>");

		w.print("<p>");
		w.print(this.contentTree.getNodeCount());
		w.println(" content nodes.</p>");

		w.println("</html></body>");
	}

	private enum NodeOrder implements Comparator<ContentNode> {
		TITLE_OR_NAME {
			@Override
			public int compare (final ContentNode a, final ContentNode b) {
				return nameOf(a).compareToIgnoreCase(nameOf(b));
			}

			private String nameOf (final ContentNode n) {
				return n.getItem() != null ? n.getItem().getTitle() :
						n.getContainer() != null ? n.getContainer().getTitle() :
								n.getFile() != null ? n.getFile().getName() : "";
			}
		};

		@Override
		public abstract int compare (final ContentNode o1, final ContentNode o2);
	}

	private static void returnStatus (final HttpServletResponse resp, final int status, final String msg) throws IOException {
		resp.reset();
		resp.setContentType("text/plain");
		resp.setStatus(status);
		resp.getWriter().println(msg);
	}

}

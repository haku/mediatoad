package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;

import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;

public class DirServlet extends HttpServlet {

	private static final long serialVersionUID = 6207424145390666199L;

	private final ContentTree contentTree;

	public DirServlet(final ContentTree contentTree) {
		this.contentTree = contentTree;
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

		if (!req.getPathInfo().endsWith(".zip")) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Only .zip supported.");
			return;
		}

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

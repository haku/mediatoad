package com.vaguehope.dlnatoad.rpc.client;

import java.io.IOException;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaBlockingStub;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaRequest;
import com.vaguehope.dlnatoad.ui.ServletCommon;

public class RemoteContentServlet extends HttpServlet {

	private static final long serialVersionUID = 752811101673893518L;
	private final RpcClient rpcClient;

	public RemoteContentServlet(final RpcClient rpcClient) {
		this.rpcClient = rpcClient;
	}

	@SuppressWarnings("resource")
	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String instanceId = getRemoteInstanceId(req, resp);
		if (instanceId == null) return;

		final String id = getIdFromPath(req, resp);
		if (id == null) return;

		final MediaBlockingStub stub;
		try {
			stub = this.rpcClient.getMediaBlockingStub(instanceId);
		}
		catch (final IllegalArgumentException e) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid remote instance ID.");
			return;
		}

		final Iterator<ReadMediaReply> replies = stub.readMedia(ReadMediaRequest.newBuilder().setId(id).build());

		final ReadMediaReply first = replies.next();
		resp.setContentType(first.getMimeType());
		resp.setContentLengthLong(first.getTotalFileLength());
		first.getContent().writeTo(resp.getOutputStream());

		while (replies.hasNext()) {
			replies.next().getContent().writeTo(resp.getOutputStream());
		}
	}

	private static String getRemoteInstanceId(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final String id = ServletCommon.firstDirFromPath(req.getPathInfo());
		if (id == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid path.");
			return null;
		}
		return id;
	}

	private static String getIdFromPath(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		final String id = ServletCommon.idFromPath(req.getPathInfo(), null);
		if (id == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "ID missing.");
			return null;
		}
		return id;
	}

}

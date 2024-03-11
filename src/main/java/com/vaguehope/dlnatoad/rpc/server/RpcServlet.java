package com.vaguehope.dlnatoad.rpc.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.vaguehope.dlnatoad.ui.ServletCommon;

import io.grpc.servlet.ServletAdapter;
import io.grpc.servlet.ServletServerBuilder;

public class RpcServlet extends HttpServlet {

	private static final long serialVersionUID = -4839739389452693420L;

	private final ServletAdapter servletAdapter;

	public RpcServlet(final MediaImpl mediaImpl) {
		this.servletAdapter = new ServletServerBuilder().addService(mediaImpl).buildServletAdapter();
	}

	@Override
	public void destroy() {
		this.servletAdapter.destroy();
		super.destroy();
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (ServletAdapter.isGrpc(req)) {
			this.servletAdapter.doPost(req, resp);
		}
		else {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST not supported.");
		}
	}

}

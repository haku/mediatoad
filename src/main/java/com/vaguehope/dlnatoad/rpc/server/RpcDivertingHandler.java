package com.vaguehope.dlnatoad.rpc.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import io.grpc.servlet.ServletAdapter;

public class RpcDivertingHandler extends AbstractHandler {

	private final Handler rpcHandler;
	private final Handler otherHandler;

	public RpcDivertingHandler(final Handler rpcHandler, final Handler otherHandler) {
		super();
		this.rpcHandler = rpcHandler;
		this.otherHandler = otherHandler;
		addBean(rpcHandler);
		addBean(otherHandler);
	}

	@Override
	public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response)
			throws IOException, ServletException {
		if (ServletAdapter.isGrpc(request)) {
			if (this.rpcHandler != null) {
				this.rpcHandler.handle(target, baseRequest, request, response);
			}
			else {
				response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
			}
		}
		else {
			this.otherHandler.handle(target, baseRequest, request, response);
		}
	}

	@Override
	public void setServer(Server server) {
		if (server == getServer())
			return;
		if (isStarted())
			throw new IllegalStateException(getState());

		super.setServer(server);
		if (this.rpcHandler != null) this.rpcHandler.setServer(server);
		this.otherHandler.setServer(server);
	}

	@Override
	public void destroy() {
		if (this.rpcHandler != null) this.rpcHandler.destroy();
		this.otherHandler.destroy();
		super.destroy();
	}

}
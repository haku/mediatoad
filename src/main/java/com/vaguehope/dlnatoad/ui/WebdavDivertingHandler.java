package com.vaguehope.dlnatoad.ui;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class WebdavDivertingHandler extends AbstractHandler {

	private final Handler webdavHandler;
	private final Handler otherHandler;

	public WebdavDivertingHandler(final Handler webdavHandler, final Handler otherHandler) {
		super();
		this.webdavHandler = webdavHandler;
		this.otherHandler = otherHandler;
		addBean(webdavHandler);
		addBean(otherHandler);
	}

	@Override
	public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
		final String method = request.getMethod();
		if ("PROPFIND".equals(method) || "OPTIONS".equals(method)) {
			this.webdavHandler.handle(target, baseRequest, request, response);
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
		this.webdavHandler.setServer(server);
		this.otherHandler.setServer(server);
	}

	@Override
	public void destroy() {
		this.webdavHandler.destroy();
		this.otherHandler.destroy();
		super.destroy();
	}

}

package com.vaguehope.dlnatoad.webdav;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.media.ContentTree;

public class WebdavSearchServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(WebdavSearchServlet.class);
	private static final long serialVersionUID = -2767194549294597918L;

	private final ContentTree contentTree;
	private final MediaDb db;

	public WebdavSearchServlet(final ContentTree contentTree, final MediaDb db) {
		this.contentTree = contentTree;
		this.db = db;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		LOG.info("info={} uri={} url={}", req.getPathInfo(), req.getRequestURI(), req.getRequestURL());
	}

}

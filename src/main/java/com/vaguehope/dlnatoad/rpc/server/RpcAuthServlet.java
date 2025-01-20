package com.vaguehope.dlnatoad.rpc.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.ui.ServletCommon;

import io.jsonwebtoken.security.PublicJwk;

public class RpcAuthServlet extends HttpServlet {

	public static final String CONTEXTPATH = "/rpcauth";
	private static final long serialVersionUID = 436332146470305421L;

	private final JwkLoader jwtLoader;

	public RpcAuthServlet(final JwkLoader jwtLoader) {
		this.jwtLoader = jwtLoader;
	}

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (!ReqAttr.ALLOW_MANAGE_RPC.get(req)) {
			ServletCommon.returnForbidden(resp);
			return;
		}

		super.service(req, resp);
	}

	@SuppressWarnings("resource")
	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("text/html");
		final PrintWriter w = resp.getWriter();
		w.println("<!DOCTYPE html><html>"
				+ "<head></head>"
				+ "<body>");
		w.println("<h1>rpc auth</h1>");

		w.println("<h2>allowed</h2>");
		w.println("<table><tr><th>username</th><th>public key thumbprint</th></tr>");
		for (final Entry<String, PublicJwk<?>> e : this.jwtLoader.getAllowedPublicKeys().entrySet()) {
			w.println(String.format("<tr><td>%s</td><td>%s</td></tr>", e.getKey(), e.getValue().thumbprint()));
		}
		w.println("</table>");

		w.println("<h2>recently rejected</h2>");
		w.println("<table><tr><th>username</th><th>public key</th><th></th></tr>");
		for (final Entry<String, PublicJwk<?>> e : this.jwtLoader.getRecentlyRejectPublicKeys().entrySet()) {
			w.println(String.format(
					"<tr><td>%s</td><td>%s</td>"
					+ "<td><form action=\"\" method=\"POST\">"
					+ "<input type=\"hidden\" name=\"action\" value=\"authadd\">"
					+ "<input type=\"hidden\" name=\"username\" value=\"%s\">"
					+ "<input type=\"hidden\" name=\"thumbprint\" value=\"%s\">"
					+ "<input type=\"submit\" value=\"Auth\">"
					+ "</form></td>"
					+ "</tr>",
					e.getKey(), e.getValue().thumbprint(), e.getKey(), e.getValue().thumbprint()));
		}
		w.println("</table>");

		w.println("</body></html>");
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String callingUsername = ReqAttr.USERNAME.get(req);

		final String keyUsername = req.getParameter("username");
		final String thumbprint = req.getParameter("thumbprint");
		if (StringUtils.isAnyBlank(keyUsername, thumbprint)) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing param.");
			return;
		}

		PublicJwk<?> pubKey = this.jwtLoader.getRecentlyRejectPublicKeys().get(keyUsername);
		if (pubKey == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Unkown.");
			return;
		}
		if (!thumbprint.equals(pubKey.thumbprint().toString())) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid thumbprint.");
			return;
		}

		this.jwtLoader.authorisePublicKey(callingUsername, keyUsername, pubKey);
		doGet(req, resp);
	}

}

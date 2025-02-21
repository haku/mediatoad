package com.vaguehope.dlnatoad.rpc.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.vaguehope.common.servlet.StatusPageServlet;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.ui.ServletCommon;

import io.jsonwebtoken.security.PublicJwk;

public class RpcAuthServlet extends StatusPageServlet {

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

	@Override
	protected void generatePageBodyHtml(final HttpServletRequest req, final HttpServletResponse resp, final PrintWriter w) {
		w.println("<h1>rpc auth</h1>");

		w.println("<h2>allowed</h2>");
		w.println("<table><tr><th>username</th><th>public key thumbprint</th><th>action</th></tr>");
		for (final Entry<String, PublicJwk<?>> e : this.jwtLoader.getAllowedPublicKeys().entrySet()) {
			w.println(String.format(
					"<tr><td>%s</td><td>%s</td>"
					+ "<td><form action=\"\" method=\"POST\">"
					+ "<input type=\"hidden\" name=\"action\" value=\"authrm\">"
					+ "<input type=\"hidden\" name=\"username\" value=\"%s\">"
					+ "<input type=\"hidden\" name=\"thumbprint\" value=\"%s\">"
					+ "<input type=\"submit\" value=\"Revoke\">"
					+ "</form></td>"
					+ "</tr>",
					e.getKey(), e.getValue().thumbprint(), e.getKey(), e.getValue().thumbprint()));
		}
		w.println("</table>");

		w.println("<h2>recently rejected</h2>");
		w.println("<table><tr><th>username</th><th>public key</th><th>action</th></tr>");
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

		final String action = req.getParameter("action");
		if ("authadd".equalsIgnoreCase(action)) {
			final PublicJwk<?> pubKey = this.jwtLoader.getRecentlyRejectPublicKeys().get(keyUsername);
			if (pubKey == null) {
				ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Unkown username.");
				return;
			}
			if (!thumbprint.equals(pubKey.thumbprint().toString())) {
				ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid thumbprint.");
				return;
			}
			this.jwtLoader.authorisePublicKey(callingUsername, keyUsername, pubKey);
		}
		else if ("authrm".equalsIgnoreCase(action)) {
			final PublicJwk<?> pubKey = this.jwtLoader.getAllowedPublicKeys().get(keyUsername);
			if (pubKey == null) {
				ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Unkown username.");
				return;
			}
			this.jwtLoader.revokePublicKey(callingUsername, keyUsername);
		}
		else {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid action.");
			return;
		}


		doGet(req, resp);
	}

}

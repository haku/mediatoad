package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.B64Code;

import com.vaguehope.dlnatoad.media.ContentTree;

public class AuthFilter implements Filter {

	private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
	private static final String BASIC_REALM = "Basic realm=\"Secure Area\"";
	private static final String HEADER_AUTHORISATION = "Authorization"; // Incoming request has this.
	private static final String HEADER_AUTHORISATION_PREFIX = "Basic "; // Incoming request starts with this.

	private final static Set<String> READ_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"GET",
			"PROPFIND"
			)));
	private final static Set<String> WRITE_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"POST"
			)));

	private final Users users;
	private final ContentTree contentTree;

	public AuthFilter(final Users users, final ContentTree contentTree) {
		this.users = users;
		this.contentTree = contentTree;
	}

	@Override
	public void init(final FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void destroy() {
	}

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse resp = (HttpServletResponse) response;

		final boolean needsAuth;
		if (WRITE_METHODS.contains(req.getMethod())) {
			if (this.users == null) {
				ServletCommon.returnStatus(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST requires --userfile.");
				return;
			}
			needsAuth = true;
		}
		else if (READ_METHODS.contains(req.getMethod())) {
			// TODO does this media need auth?
			// if yes and users==null, return error.
			// otherwise needsAuth implies users!=null.
			needsAuth = false;
		}
		else {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method not supported.");
			return;
		}

//		final String id = ServletCommon.idFromPath(req.getPathInfo(), null);
//		ContentNode node = this.contentTree.getNode(id);
//		if (node == null) {
//			final ContentItem item = this.contentTree.getItem(id);
//			if (item.getParentId() != null) {
//				node = this.contentTree.getNode(item.getParentId());
//			}
//		}
//		if (node != null) {
//			// TODO auth?
//		}

		if (!needsAuth) {
			chain.doFilter(request, response);
			return;
		}

		// TODO check for cookie.

		// Request basic auth.
		String authHeader64 = req.getHeader(HEADER_AUTHORISATION);
		if (authHeader64 == null
				|| authHeader64.length() < HEADER_AUTHORISATION_PREFIX.length() + 3
				|| !authHeader64.startsWith(HEADER_AUTHORISATION_PREFIX)) {
			send401(resp);
			return;
		}

		// Verify password.
		authHeader64 = authHeader64.substring(HEADER_AUTHORISATION_PREFIX.length());
		final String authHeader = B64Code.decode(authHeader64, (String) null);
		final int x = authHeader.indexOf(":");
		if (x <= 0) {
			send401(resp);
			return;
		}
		final String user = authHeader.substring(0, x);
		final String pass = authHeader.substring(x + 1);
		if (user == null || pass == null || user.isEmpty() || pass.isEmpty() || !this.users.validUser(user, pass)) {
			send401(resp);
			return;
		}

		// TODO set cookie.

		chain.doFilter(request, response);
	}

	private static void send401(final HttpServletResponse resp) throws IOException {
		resp.setHeader(WWW_AUTHENTICATE, BASIC_REALM);
		resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}

}

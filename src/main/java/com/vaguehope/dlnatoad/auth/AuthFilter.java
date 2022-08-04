package com.vaguehope.dlnatoad.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.B64Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.ui.ServletCommon;
import com.vaguehope.dlnatoad.ui.Users;

public class AuthFilter implements Filter {

	private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
	private static final String BASIC_REALM = "Basic realm=\"Secure Area\"";
	private static final String HEADER_AUTHORISATION = "Authorization"; // Incoming request has this.
	private static final String HEADER_AUTHORISATION_PREFIX = "Basic "; // Incoming request starts with this.
	private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);

	private final static Set<String> READ_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"GET",
			"PROPFIND"
			)));
	private final static Set<String> WRITE_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"POST"
			)));

	private final Users users;
	private final AuthTokens authTokens;
	private final ContentTree contentTree;
	private final boolean printAccessLog;

	public AuthFilter(final Users users, final AuthTokens authTokens, final ContentTree contentTree, final boolean printAccessLog) {
		this.users = users;
		this.authTokens = authTokens;
		this.contentTree = contentTree;
		this.printAccessLog = printAccessLog;
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

		String username = null;
		final Cookie tokenCookie = ServletCommon.findCookie(req, Auth.TOKEN_COOKIE_NAME);
		if (tokenCookie != null) {
			username = this.authTokens.usernameForToken(tokenCookie.getValue());
			// Continue even if token is invalid as auth may be attached or may not be required.
		}

		if (username == null && this.users != null) {
			String authHeader64 = req.getHeader(HEADER_AUTHORISATION);
			if (authHeader64 != null
					&& authHeader64.length() >= HEADER_AUTHORISATION_PREFIX.length() + 3
					&& authHeader64.startsWith(HEADER_AUTHORISATION_PREFIX)) {
				authHeader64 = authHeader64.substring(HEADER_AUTHORISATION_PREFIX.length());
				final String authHeader = B64Code.decode(authHeader64, (String) null);
				final int x = authHeader.indexOf(":");
				if (x <= 0) {
					send401(resp);
					logRequest(req, resp, "Rejected malformed auth: {}", authHeader);
					return;
				}
				final String user = authHeader.substring(0, x);
				final String pass = authHeader.substring(x + 1);
				if (user == null || pass == null || user.isEmpty() || pass.isEmpty()) {
					send401(resp);
					logRequest(req, resp, "Rejected missing creds for user: {}", user);
					return;
				}
				if (!this.users.validUser(user, pass)) {
					send401(resp);
					logRequest(req, resp, "Rejected invalid creds for user: {}", user);
					return;
				}
				setTokenCookie(user, resp);
				username = user;
				logRequest(req, resp, "Accepted creds for user: {}", username);
			}
		}

		// Do not prompt for login if already logged in cos that will cause a loop.
		if (username == null && "login".equalsIgnoreCase(req.getParameter("action"))) {
			promptForLogin(resp);
			return;
		}

		if (username != null) {
			req.setAttribute(C.USERNAME_ATTR, username);
		}

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
			needsAuth = false;
		}
		else {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method not supported.");
			return;
		}

		if (needsAuth && username == null) {
			send401(resp);
			logRequest(req, resp, "Rejected request from unknown user when auth is required.");
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

		chain.doFilter(request, response);
	}

	private void logRequest(HttpServletRequest req, HttpServletResponse resp, String msg, String... args) {
		if (!this.printAccessLog) return;
		Object[] allArgs = new Object[4 + args.length];
		allArgs[0] = resp.getStatus();
		allArgs[1] = req.getMethod();
		allArgs[2] = req.getPathInfo();
		allArgs[3] = req.getRemoteAddr();
		System.arraycopy(args, 0, allArgs, 4, args.length);
		LOG.info("{} {} {} {} " + msg, allArgs);
	}

	private static void send401(final HttpServletResponse resp) throws IOException {
		resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}

	private static void promptForLogin(final HttpServletResponse resp) throws IOException {
		resp.setHeader(WWW_AUTHENTICATE, BASIC_REALM);
		resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}

	private void setTokenCookie (final String username, final HttpServletResponse resp) throws IOException {
		final String token = this.authTokens.newToken(username);
		final Cookie cookie = new Cookie(Auth.TOKEN_COOKIE_NAME, token);
		cookie.setMaxAge((int) TimeUnit.MILLISECONDS.toSeconds(Auth.MAX_TOKEN_AGE_MILLIS));
		cookie.setPath("/");
		resp.addCookie(cookie);
	}

}

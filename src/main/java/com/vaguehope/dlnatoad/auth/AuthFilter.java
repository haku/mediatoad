package com.vaguehope.dlnatoad.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
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

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.auth.Users.User;
import com.vaguehope.dlnatoad.ui.ServletCommon;

public class AuthFilter implements Filter {

	private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
	private static final String BASIC_REALM = "Basic realm=\"Secure Area\"";
	private static final String HEADER_AUTHORISATION = "Authorization"; // Incoming request has this.
	private static final String HEADER_AUTHORISATION_PREFIX = "Basic "; // Incoming request starts with this.
	private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);

	private final static Set<String> READ_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"GET",
			"OPTIONS",
			"PROPFIND"
			)));
	private final static Set<String> WRITE_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"POST"
			)));

	private final Users users;
	private final AuthTokens authTokens;
	private final boolean printAccessLog;

	public AuthFilter(final Users users, final AuthTokens authTokens, final boolean printAccessLog) {
		this.users = users;
		this.authTokens = authTokens;
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

		User user = null;
		final Cookie tokenCookie = ServletCommon.findCookie(req, Auth.TOKEN_COOKIE_NAME);
		if (tokenCookie != null) {
			final String username = this.authTokens.usernameForToken(tokenCookie.getValue());
			if (username != null) {
				user = this.users.getUser(username);
			}
			else {
				// Clean up invalid tokens.
				clearTokenCookie(resp);
			}

			// Continue even if token is invalid as auth may be attached or may not be required.
		}

		if (this.users != null && user == null && (isPost(req) || isLoginRequest(req))) {
			String authHeader64 = req.getHeader(HEADER_AUTHORISATION);
			if (authHeader64 != null
					&& authHeader64.length() >= HEADER_AUTHORISATION_PREFIX.length() + 3
					&& authHeader64.startsWith(HEADER_AUTHORISATION_PREFIX)) {
				authHeader64 = authHeader64.substring(HEADER_AUTHORISATION_PREFIX.length());
				final String authHeader = new String(Base64.getDecoder().decode(authHeader64), StandardCharsets.UTF_8);
				final int x = authHeader.indexOf(":");
				if (x <= 0) {
					send401AndMaybePromptLogin(req, resp);
					logRequest(req, resp, "Rejected malformed auth: {}", authHeader);
					return;
				}
				final String username = authHeader.substring(0, x);
				final String pass = authHeader.substring(x + 1);
				if (username == null || pass == null || username.isEmpty() || pass.isEmpty()) {
					send401AndMaybePromptLogin(req, resp);
					logRequest(req, resp, "Rejected missing creds for user: {}", username);
					return;
				}
				user = this.users.validUser(username, pass);
				if (user == null) {
					send401AndMaybePromptLogin(req, resp);
					logRequest(req, resp, "Rejected invalid creds for user: {}", username);
					return;
				}
				setTokenCookie(user.getUsername(), resp);
				logRequest(req, resp, "Accepted creds for user: {}", user.getUsername());
			}
			else {
				send401AndMaybePromptLogin(req, resp);
				return;
			}
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

		if (needsAuth && user == null) {
			send401AndMaybePromptLogin(req, resp);
			logRequest(req, resp, "Rejected request from unknown user when auth is required.");
			return;
		}


		if (user != null) {
			ReqAttr.USERNAME.set(req, user.getUsername());
			ReqAttr.ALLOW_REMOTE_SEARCH.set(req, Boolean.TRUE);
			ReqAttr.ALLOW_UPNP_INSPECTOR.set(req, Boolean.TRUE);

			if (user.hasPermission(Permission.EDITTAGS)) {
				ReqAttr.ALLOW_EDIT_TAGS.set(req, Boolean.TRUE);
			}
			if (user.hasPermission(Permission.EDITDIRPREFS)) {
				ReqAttr.ALLOW_EDIT_DIR_PREFS.set(req, Boolean.TRUE);
			}
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

	private void logRequest(final HttpServletRequest req, final HttpServletResponse resp, final String msg, final String... args) {
		if (!this.printAccessLog) return;
		final Object[] allArgs = new Object[4 + args.length];
		allArgs[0] = resp.getStatus();
		allArgs[1] = req.getMethod();
		allArgs[2] = req.getPathInfo();
		allArgs[3] = req.getRemoteAddr();
		System.arraycopy(args, 0, allArgs, 4, args.length);
		LOG.info("{} {} {} {} " + msg, allArgs);
	}

	private static boolean isGet(final HttpServletRequest req) {
		return "GET".equals(req.getMethod());
	}

	private static boolean isPost(final HttpServletRequest req) {
		return "POST".equals(req.getMethod());
	}

	private static boolean isLoginRequest(final HttpServletRequest req) {
		return "login".equalsIgnoreCase(req.getParameter("action"));
	}

	private static void send401AndMaybePromptLogin(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		if (isGet(req) || isLoginRequest(req)) {
			resp.setHeader(WWW_AUTHENTICATE, BASIC_REALM);
		}
		resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}

	private void setTokenCookie (final String username, final HttpServletResponse resp) throws IOException {
		final String token = this.authTokens.newToken(username);
		final Cookie cookie = makeAuthCookie(token);
		cookie.setMaxAge((int) TimeUnit.MILLISECONDS.toSeconds(Auth.MAX_TOKEN_AGE_MILLIS));
		resp.addCookie(cookie);
	}

	private static void clearTokenCookie(final HttpServletResponse resp) throws IOException {
		final Cookie cookie = makeAuthCookie("");
		cookie.setMaxAge(0);
		resp.addCookie(cookie);
	}

	private static Cookie makeAuthCookie(final String token) {
		final Cookie cookie = new Cookie(Auth.TOKEN_COOKIE_NAME, token);
		cookie.setPath("/");
		cookie.setHttpOnly(true);
		cookie.setComment(HttpCookie.getCommentWithAttributes("", false, SameSite.STRICT));
		return cookie;
	}

}

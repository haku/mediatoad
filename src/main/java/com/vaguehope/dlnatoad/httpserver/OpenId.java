package com.vaguehope.dlnatoad.httpserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.openid.OpenIdAuthenticator;
import org.eclipse.jetty.security.openid.OpenIdConfiguration;
import org.eclipse.jetty.security.openid.OpenIdLoginService;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.Args.ArgsException;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.auth.Users;
import com.vaguehope.dlnatoad.auth.Users.User;

public class OpenId {

	private static final String OPENID_TOKEN_COOKIE_NAME = "MEDIATOADOPENIDTOKEN";

	private final String issuerUri;
	private final String clientId;
	private final String clientSecret;
	private final boolean insecure;
	private final Users users;

	public OpenId(final Args args, final Users users) throws ArgsException, IOException {
		this.issuerUri = args.getOpenIdIssuerUri();
		this.clientId = args.getOpenIdClientId();
		this.clientSecret = FileUtils.readLines(args.getOpenIdClientSecretFile(), StandardCharsets.UTF_8).get(0);
		this.insecure = args.isOpenIdInsecure();
		this.users = users;
	}

	public void addToHandler(final ServletContextHandler handler) {
		final OpenIdConfiguration openIdConfig = new OpenIdConfiguration(this.issuerUri, this.clientId, this.clientSecret);
		openIdConfig.addScopes("email", "profile");
		openIdConfig.setLogoutWhenIdTokenIsExpired(true);
		openIdConfig.setAuthenticateNewUsers(false);

		final OpenIdLoginService loginService = new OpenIdLoginService(openIdConfig);
		loginService.setIdentityService(new DefaultIdentityService());

		final OpenIdAuthenticator authenticator = new OpenIdAuthenticator(openIdConfig);
		authenticator.setRedirectPath("/mediatoad/security_check");

		final ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
		securityHandler.setLoginService(loginService);
		securityHandler.setAuthenticator(authenticator);
		securityHandler.setRealmName(this.issuerUri);

		final SessionHandler sessionHandler = new SessionHandler();
		sessionHandler.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
		sessionHandler.getSessionCookieConfig().setName(OPENID_TOKEN_COOKIE_NAME);
		sessionHandler.getSessionCookieConfig().setHttpOnly(true);
		sessionHandler.getSessionCookieConfig().setSecure(!this.insecure);
		// why not STRICT: when redirecting back from openid provider the session cookie is not sent, so session is reset.
		// why not partitioned: same reason.
		sessionHandler.getSessionCookieConfig().setComment(HttpCookie.getCommentWithAttributes("", true, SameSite.LAX, false));
		sessionHandler.setRefreshCookieAge((int) TimeUnit.HOURS.toSeconds(1));
		sessionHandler.getSessionCookieConfig().setMaxAge((int) TimeUnit.DAYS.toSeconds(7));
		sessionHandler.setMaxInactiveInterval((int) TimeUnit.DAYS.toSeconds(7));
		sessionHandler.setHandler(securityHandler);

		handler.setSessionHandler(sessionHandler);
		handler.setSecurityHandler(securityHandler);
		handler.addFilter(new FilterHolder(new OpenIdLoginFilter()), "/*", null);
		handler.addServlet(new ServletHolder(new LogoutServlet()), "/logout");
	}

	/**
	 * This is to work around an issue where org.eclipse.jetty.server.Request.authenticate()
	 * always throws ServletException even when everything is working fine,
	 * because it does not know about org.eclipse.jetty.server.Authentication.Challenge.
	 */
	protected static void startAuthFlow(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, ServletException {
		final Request jreq = (Request) req;
		final Authentication auth = jreq.getAuthentication();
		if (auth == null) throw new IllegalStateException("auth system is not configured.");
		if (!(auth instanceof Authentication.Deferred)) throw new IllegalStateException("auth was not correct class: " + auth.getClass());
		final Authentication newAuth = ((Authentication.Deferred) auth).authenticate(req, resp);
		jreq.setAuthentication(newAuth);

		if (!(newAuth instanceof Authentication.Challenge)) {
			// If got this far but did not return a challenge, then something is broken in the openid config.
			resp.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500);
			return;
		}
	}

	private class OpenIdLoginFilter implements Filter {
		@Override
		public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
			final HttpServletRequest req = (HttpServletRequest) request;
			final HttpServletResponse resp = (HttpServletResponse) response;

			final Principal userPrincipal = req.getUserPrincipal();
			final String username = userPrincipal != null ? userPrincipal.getName() : null;

			if (username == null && "openidlogin".equals(req.getParameter("action"))) {
				startAuthFlow(req, resp);
				return;
			}

			if (username != null) {
				final User user = OpenId.this.users.getUser(username);
				if (user == null) {
					resp.sendError(HttpServletResponse.SC_FORBIDDEN, "User '" + username + "' not found in userfile.");
					return;
				}
				ReqAttr.USER.set(req, user);
			}

			chain.doFilter(request, response);
		}
	}

	@SuppressWarnings("serial")
	private static class LogoutServlet extends HttpServlet {
		@Override
		protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
			req.logout();
		}
	}

}

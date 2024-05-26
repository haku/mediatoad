package com.vaguehope.dlnatoad.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.FilterChain;
import javax.servlet.http.Cookie;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.junit.Before;
import org.junit.Test;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.auth.Users.User;

public class AuthFilterTest {

	private Users users;
	private AuthTokens authTokens;
	private AuthFilter undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;
	private FilterChain chain;

	@Before
	public void before() throws Exception {
		this.users = mock(Users.class);
		this.authTokens = mock(AuthTokens.class);
		this.undertest = new AuthFilter(this.users, this.authTokens, true);
		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
		this.chain = mock(FilterChain.class);
	}

// Always:

	@Test
	public void itBlocksUnknownMethod() throws Exception {
		this.req.setMethod("PUT");
		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(405, this.resp.getStatus());

		verifyNoInteractions(this.chain);
	}

// Auth Disabled:

	@Test
	public void itAllowsGetWhenNoUsers() throws Exception {
		this.undertest = new AuthFilter(null, this.authTokens, true);
		this.req.setMethod("GET");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itBlocksPostWhenNoUsers() throws Exception {
		this.undertest = new AuthFilter(null, this.authTokens, true);
		this.req.setMethod("POST");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(405, this.resp.getStatus());
		assertEquals("POST requires --userfile.\n", this.resp.getContentAsString());
		verifyNoInteractions(this.chain);
	}

// Auth Enabled, Not Logged In:

	@Test
	public void itAllowsGetRootForAllUsers() throws Exception {
		this.req.setMethod("GET");
		this.req.setPathInfo("/");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itBlocksPostWithNoSession() throws Exception {
		this.req.setMethod("POST");
		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(401, this.resp.getStatus());
		assertThat((Collection<?>) this.resp.getHeaderNames(), hasSize(0));
		verifyNoInteractions(this.chain);
	}

	@Test
	public void itIgnoresWrongCredsForGetThatDoesNotRequireAuth() throws Exception {
		this.req.setMethod("GET");
		this.req.setPathInfo("/");
		setInvalidCreds();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertNull(ReqAttr.USERNAME.get(this.req));
		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itIgnoresAndClearsInvalidtokenForGetThatDoesNotRequireAuth() throws Exception {
		this.req.setMethod("GET");
		this.req.setPathInfo("/");
		setInvalidSessionToken();
		when(this.authTokens.usernameForToken(anyString())).thenReturn(null);

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertNull(ReqAttr.USERNAME.get(this.req));

		final Cookie cookie = getSingleCookie();
		assertEquals("DLNATOADTOKEN", cookie.getName());
		assertEquals("/", cookie.getPath());
		assertEquals(0, cookie.getMaxAge());
		assertEquals("", cookie.getValue());

		verify(this.chain).doFilter(this.req, this.resp);
	}

// Auth Enabled, Prompt For Login:

	@Test
	public void itPromptsForLoginWithActionParam() throws Exception {
		this.req.setMethod("GET");
		setLoginAction();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(401, this.resp.getStatus());
		assertEquals("Basic realm=\"Secure Area\"", this.resp.getHeader("WWW-Authenticate"));
		verifyNoInteractions(this.chain);
	}

	@Test
	public void itPromptsForLoginWithActionParamAndWrongCreds() throws Exception {
		this.req.setMethod("GET");
		setLoginAction();
		setInvalidCreds();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(401, this.resp.getStatus());
		assertEquals("Basic realm=\"Secure Area\"", this.resp.getHeader("WWW-Authenticate"));
		verifyNoInteractions(this.chain);
	}

// Auth Enabled, Valid Creds:

	@Test
	public void itChecksValidAuthForPost() throws Exception {
		this.req.setMethod("POST");
		setValidCreds();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itDoesAuthorizedGetAndAddsRequestAttributesIfActionIsSet() throws Exception {
		this.req.setMethod("GET");
		setLoginAction();
		setValidCreds();
		when(this.authTokens.newToken("h4cker")).thenReturn("the-secret-token");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertEquals("h4cker", ReqAttr.USERNAME.get(this.req));

		final Cookie cookie = getSingleCookie();
		assertEquals("DLNATOADTOKEN", cookie.getName());
		assertThat(cookie.getMaxAge(), greaterThan(1));
		assertEquals("the-secret-token", cookie.getValue());
		assertEquals("/", cookie.getPath());
		assertEquals(true, cookie.isHttpOnly());
		assertEquals("__SAME_SITE_STRICT__", cookie.getComment());
		assertEquals(SameSite.STRICT, HttpCookie.getSameSiteFromComment(cookie.getComment()));

		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itAcceptsAuthAlongSideOldInvalidTokenIfActionIsSet() throws Exception {
		this.req.setMethod("GET");
		setLoginAction();
		setValidCreds();
		setInvalidSessionToken();

		when(this.authTokens.newToken("h4cker")).thenReturn("new-token");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertEquals("h4cker", ReqAttr.USERNAME.get(this.req));

		verify(this.chain).doFilter(this.req, this.resp);
	}

// Auth Enabled, Logged In:

	@Test
	public void itAcceptsValidToken() throws Exception {
		this.req.setMethod("GET");
		setValidSessionToken();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertEquals("h4cker", ReqAttr.USERNAME.get(this.req));
		assertThat(this.resp.getCookies(), emptyArray());

		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itDoesNotResendSessionCookieIfValidToken() throws Exception {
		this.req.setMethod("GET");
		setValidSessionToken();
		setValidCreds();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertEquals("h4cker", ReqAttr.USERNAME.get(this.req));
		assertThat(this.resp.getCookies(), emptyArray());
	}

	@Test
	public void itDoesNotPromptForLoginWithActionParamIfAlreadyLoggedIn() throws Exception {
		this.req.setMethod("GET");
		setLoginAction();
		setValidSessionToken();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
	}

// Auth Enabled, Rejecting Invalid Creds:

	@Test
	public void itRejectsWrongCredsForPost() throws Exception {
		this.req.setMethod("POST");
		setInvalidCreds();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(401, this.resp.getStatus());
		assertThat((Collection<?>) this.resp.getHeaderNames(), hasSize(0));
		verifyNoInteractions(this.chain);
	}

	@Test
	public void itRejectsInvalidTokenForPost() throws Exception {
		this.req.setMethod("POST");
		setInvalidSessionToken();

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(401, this.resp.getStatus());
		assertThat((Collection<?>) this.resp.getHeaderNames(), hasSize(0));
		verifyNoInteractions(this.chain);
	}

// Helpers:

	private void setLoginAction() {
		this.req.setParameter("action", "login");
	}

	private void setValidCreds() {
		this.req.addHeader("Authorization", "Basic aDRja2VyOmh1bnRlcjI=");
		final User user = new User("h4cker", null, null);
		when(this.users.validUser("h4cker", "hunter2")).thenReturn(user);
		when(this.users.getUser("h4cker")).thenReturn(user);
	}

	private void setInvalidCreds() {
		this.req.addHeader("Authorization", "Basic aDRja2VyOmh1bnRlcg==");
	}

	private void setValidSessionToken() throws IOException {
		setSessionTokenCookie("my-session-token");
		when(this.authTokens.usernameForToken("my-session-token")).thenReturn("h4cker");
		final User user = new User("h4cker", null, null);
		when(this.users.getUser("h4cker")).thenReturn(user);
	}

	private void setInvalidSessionToken() {
		setSessionTokenCookie("my-invalid-token");
	}

	private void setSessionTokenCookie(String token) {
		Cookie cookie = new Cookie("DLNATOADTOKEN", token);
		this.req.setCookies(new Cookie[] { cookie });
	}

	private Cookie getSingleCookie() {
		final Cookie[] cookies = this.resp.getCookies();
		if (cookies.length != 1) fail("Expected 1 cookie: " + Arrays.toString(cookies));
		return cookies[0];
	}

}

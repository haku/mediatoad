package com.vaguehope.dlnatoad.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;

import javax.servlet.FilterChain;
import javax.servlet.http.Cookie;

import org.junit.Before;
import org.junit.Test;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.media.ContentTree;

public class AuthFilterTest {

	private Users users;
	private AuthTokens authTokens;
	private ContentTree contentTree;
	private AuthFilter undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;
	private FilterChain chain;

	@Before
	public void before() throws Exception {
		this.users = mock(Users.class);
		this.authTokens = mock(AuthTokens.class);
		this.contentTree = mock(ContentTree.class);
		this.undertest = new AuthFilter(this.users, this.authTokens, this.contentTree, true);
		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
		this.chain = mock(FilterChain.class);
	}

	@Test
	public void itBlocksPostWhenNoUsers() throws Exception {
		this.undertest = new AuthFilter(null, this.authTokens, this.contentTree, true);
		this.req.setMethod("POST");
		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(405, this.resp.getStatus());
		assertEquals("POST requires --userfile.\n", this.resp.getContentAsString());
		verifyNoInteractions(this.chain);
		verifyNoInteractions(this.contentTree);
	}

	@Test
	public void itAllowsGetWhenNoUsers() throws Exception {
		this.undertest = new AuthFilter(null, this.authTokens, this.contentTree, true);
		this.req.setMethod("GET");
		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
		verifyNoInteractions(this.contentTree);
	}

	@Test
	public void itAllowsGetRootForAllUsers() throws Exception {
		this.req.setMethod("GET");
		this.req.setPathInfo("/");
		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
		verifyNoInteractions(this.contentTree);
	}

	@Test
	public void itBlocksUnknownMethod() throws Exception {
		this.req.setMethod("PUT");
		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(405, this.resp.getStatus());
		verifyNoInteractions(this.chain);
		verifyNoInteractions(this.contentTree);
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
	public void itPromptsForLoginWithActionParam() throws Exception {
		this.req.setMethod("GET");
		this.req.setParameter("action", "login");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(401, this.resp.getStatus());
		assertEquals("Basic realm=\"Secure Area\"", this.resp.getHeader("WWW-Authenticate"));
		verifyNoInteractions(this.chain);
	}

	@Test
	public void itDoesNotPromptForLoginWithActionParamIfAlreadyLoggedIn() throws Exception {
		this.req.setMethod("GET");
		this.req.setParameter("action", "login");
		setSessionTokenCookie("my-session-token");
		when(this.authTokens.usernameForToken("my-session-token")).thenReturn("h4cker");

		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itRejectsWrongCredsForPost() throws Exception {
		this.req.setMethod("POST");
		this.req.addHeader("Authorization", "Basic aDRja2VyOmh1bnRlcg==");
		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(401, this.resp.getStatus());
		verifyNoInteractions(this.chain);
	}

	@Test
	public void itChecksValidAuthForPost() throws Exception {
		this.req.setMethod("POST");
		this.req.addHeader("Authorization", "Basic aDRja2VyOmh1bnRlcjI=");
		when(this.users.validUser("h4cker", "hunter2")).thenReturn(true);
		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itDoesAuthorizedGetAndAddsRequestAttributes() throws Exception {
		this.req.setMethod("GET");
		this.req.addHeader("Authorization", "Basic aDRja2VyOmh1bnRlcjI=");
		when(this.users.validUser("h4cker", "hunter2")).thenReturn(true);
		when(this.authTokens.newToken("h4cker")).thenReturn("the-secret-token");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertEquals("h4cker", ReqAttr.USERNAME.get(this.req));

		final Cookie[] cookies = this.resp.getCookies();
		if (cookies.length != 1) fail("Expected 1 cookie: " + Arrays.toString(cookies));
		final Cookie cookie = cookies[0];
		assertEquals("DLNATOADTOKEN", cookie.getName());
		assertEquals("the-secret-token", cookie.getValue());
		assertEquals("/", cookie.getPath());

		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itAcceptsAuthAlongSideOldInvalidToken() throws Exception {
		this.req.setMethod("GET");
		this.req.addHeader("Authorization", "Basic aDRja2VyOmh1bnRlcjI=");
		setSessionTokenCookie("old-token");

		when(this.authTokens.usernameForToken("old-token")).thenReturn(null);
		when(this.users.validUser("h4cker", "hunter2")).thenReturn(true);
		when(this.authTokens.newToken("h4cker")).thenReturn("new-token");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertEquals("h4cker", ReqAttr.USERNAME.get(this.req));
		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itAcceptsValidToken() throws Exception {
		this.req.setMethod("GET");
		setSessionTokenCookie("my-session-token");
		when(this.authTokens.usernameForToken("my-session-token")).thenReturn("h4cker");

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertEquals("h4cker", ReqAttr.USERNAME.get(this.req));
		assertThat(this.resp.getCookies(), emptyArray());

		verify(this.chain).doFilter(this.req, this.resp);
	}

	@Test
	public void itRejectsInvalidTokenForPost() throws Exception {
		this.req.setMethod("POST");
		setSessionTokenCookie("my-session-token");
		when(this.authTokens.usernameForToken(anyString())).thenReturn(null);

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(401, this.resp.getStatus());
		verifyNoInteractions(this.chain);
	}

	@Test
	public void itIgnoresInvalidtokenForGetThatDoesNotRequireAuth() throws Exception {
		this.req.setMethod("GET");
		this.req.setPathInfo("/");
		setSessionTokenCookie("my-session-token");
		when(this.authTokens.usernameForToken(anyString())).thenReturn(null);

		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(200, this.resp.getStatus());
		verify(this.chain).doFilter(this.req, this.resp);
		verifyNoInteractions(this.contentTree);
	}

	private void setSessionTokenCookie(String token) {
		Cookie cookie = new Cookie("DLNATOADTOKEN", token);
		cookie.setPath("/");
		cookie.setMaxAge(123);
		this.req.setCookies(new Cookie[] { cookie });
	}

}

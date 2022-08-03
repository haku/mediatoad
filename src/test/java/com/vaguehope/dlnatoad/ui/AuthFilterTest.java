package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import javax.servlet.FilterChain;

import org.junit.Before;
import org.junit.Test;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.media.ContentTree;

public class AuthFilterTest {

	private Users users;
	private ContentTree contentTree;
	private AuthFilter undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;
	private FilterChain chain;

	@Before
	public void before() throws Exception {
		this.users = mock(Users.class);
		this.contentTree = mock(ContentTree.class);
		this.undertest = new AuthFilter(this.users, this.contentTree);
		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
		this.chain = mock(FilterChain.class);
	}

	@Test
	public void itBlocksPostWhenNoUsers() throws Exception {
		this.undertest = new AuthFilter(null, this.contentTree);
		this.req.setMethod("POST");
		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(405, this.resp.getStatus());
		assertEquals("POST requires --userfile.\n", this.resp.getContentAsString());
		verifyNoInteractions(this.chain);
		verifyNoInteractions(this.contentTree);
	}

	@Test
	public void itAllowsGetWhenNoUsers() throws Exception {
		this.undertest = new AuthFilter(null, this.contentTree);
		this.req.setMethod("GET");
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
	public void itPromptsForAuthForPost() throws Exception {
		this.req.setMethod("POST");
		this.undertest.doFilter(this.req, this.resp, this.chain);
		assertEquals(401, this.resp.getStatus());
		assertEquals("Basic realm=\"Secure Area\"", this.resp.getHeader("WWW-Authenticate"));
		verifyNoInteractions(this.chain);
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

		this.undertest.doFilter(this.req, this.resp, this.chain);

		assertEquals(200, this.resp.getStatus());
		assertEquals("h4cker", this.req.getAttribute(C.USERNAME_ATTR));
		verify(this.chain).doFilter(this.req, this.resp);
	}

}

package com.vaguehope.dlnatoad;

import javax.servlet.http.HttpServletResponse;

import org.teleal.common.mock.http.MockHttpServletResponse;

public class MyMockHttpServletResponse extends MockHttpServletResponse implements HttpServletResponse {

	@Override
	public void setContentLengthLong(long len) {
		setContentLength(Math.toIntExact(len));
	}

	@Override
	public String getHeader(String name) {
		return (String) super.getHeader(name);
	}

}

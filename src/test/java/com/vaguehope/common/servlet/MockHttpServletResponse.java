package com.vaguehope.common.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

public class MockHttpServletResponse implements HttpServletResponse {

	private String contentType;
	private final Map<String, String> headers = new HashMap<>();
	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	private final PrintWriter printWriter = new PrintWriter(this.outputStream);
	private final MockServletOutputStream servletOutputStream = new MockServletOutputStream(this.outputStream);
	private int status;
	private List<Cookie> cookies = new ArrayList<>();
	private int contentLength;

	public MockHttpServletResponse() {
		reset();
	}

	public String getOutputAsString () throws IOException {
		this.printWriter.flush();
		this.servletOutputStream.flush();
		return new String(this.outputStream.toByteArray());
	}

	public byte[] getOutputAsByteArray() throws IOException {
		this.printWriter.flush();
		this.servletOutputStream.flush();
		return this.outputStream.toByteArray();
	}

	public List<Cookie> getCookies() {
		return this.cookies;
	}

	public int getContentLength() {
		return this.contentLength;
	}

	// HttpServletResponse

	@Override
	public void reset () {
		this.contentType = null;
		this.headers.clear();
		this.outputStream.reset();
		this.status = 200;
		this.cookies.clear();
	}

	@Override
	public void setContentType (final String contentType) {
		this.contentType = contentType;
	}

	@Override
	public String getContentType () {
		return this.contentType;
	}

	@Override
	public void setContentLength (final int length) {
		this.contentLength = length;
	}

	@Override
	public void setContentLengthLong(long len) {
		setContentLength(Math.toIntExact(len));
	}

	@Override
	public void addHeader (final String name, final String value) {
		this.headers.put(name, value);
	}

	@Override
	public void setHeader (final String name, final String value) {
		addHeader(name, value);
	}

	@Override
	public String getHeader (final String name) {
		return this.headers.get(name);
	}

	@Override
	public Collection<String> getHeaderNames () {
		return this.headers.keySet();
	}

	@Override
	public Collection<String> getHeaders (final String name) {
		return Collections.singleton(getHeader(name));
	}

	@Override
	public void setDateHeader (final String name, final long value) {
		this.headers.put(name, String.valueOf(value));
	}

	@Override
	public ServletOutputStream getOutputStream () throws IOException {
		return this.servletOutputStream;
	}

	@Override
	public PrintWriter getWriter () throws IOException {
		return this.printWriter;
	}

	@Override
	public void flushBuffer () throws IOException {
		this.servletOutputStream.flush();
	}

	@Override
	public int getStatus () {
		return this.status;
	}

	@Override
	public void setStatus (final int status) {
		this.status = status;
	}

	@Override
	public void setStatus (final int status, final String statusMsg) {
		this.status = status;
	}

	@Override
	public boolean isCommitted () {
		try {
			return !StringUtils.isAllBlank(getOutputAsString());
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void addCookie (final Cookie cookie) {
		this.cookies.add(cookie);
	}

	@Override
	public void sendError (final int s) throws IOException {
		this.status = s;
	}

	@Override
	public void setCharacterEncoding (final String arg0) {
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	@Override
	public int getBufferSize () {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public String getCharacterEncoding () {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public Locale getLocale () {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void resetBuffer () {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void setBufferSize (final int arg0) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void setLocale (final Locale arg0) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void addDateHeader (final String arg0, final long arg1) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void addIntHeader (final String arg0, final int arg1) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public boolean containsHeader (final String arg0) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public String encodeRedirectURL (final String arg0) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public String encodeRedirectUrl (final String arg0) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public String encodeURL (final String arg0) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public String encodeUrl (final String arg0) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void sendError (final int arg0, final String arg1) throws IOException {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void sendRedirect (final String arg0) throws IOException {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void setIntHeader (final String arg0, final int arg1) {
		throw new UnsupportedOperationException("Not implemented.");
	}

}

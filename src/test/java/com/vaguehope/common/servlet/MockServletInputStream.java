package com.vaguehope.common.servlet;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

public class MockServletInputStream extends ServletInputStream {

	private final InputStream inputStream;

	public MockServletInputStream(final InputStream inputStream) {
		this.inputStream = inputStream;
	}

	@Override
	public int read() throws IOException {
		return this.inputStream.read();
	}

	@Override
	public boolean isFinished() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public boolean isReady() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void setReadListener(final ReadListener readListener) {
		throw new UnsupportedOperationException("Not implemented.");
	}

}

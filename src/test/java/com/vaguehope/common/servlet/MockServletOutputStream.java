package com.vaguehope.common.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

public class MockServletOutputStream extends ServletOutputStream {

	private final ByteArrayOutputStream outputStream;

	public MockServletOutputStream (final ByteArrayOutputStream outputStream) {
		this.outputStream = outputStream;
	}

	@Override
	public void write (final int b) throws IOException {
		this.outputStream.write(b);
	}

	@Override
	public boolean isReady() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void setWriteListener(WriteListener writeListener) {
		throw new UnsupportedOperationException("Not implemented.");
	}

}

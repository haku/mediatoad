package com.vaguehope.dlnatoad.util;

import java.io.IOException;
import java.io.InputStream;

public class InputStremWrapper extends InputStream {

	private final InputStream wrapped;

	public InputStremWrapper(final InputStream wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public int read() throws IOException {
		return this.wrapped.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return this.wrapped.read(b);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return this.wrapped.read(b, off, len);
	}


	@Override
	public byte[] readAllBytes() throws IOException {
		return this.wrapped.readAllBytes();
	}

	@Override
	public int readNBytes(byte[] b, int off, int len) throws IOException {
		return this.wrapped.readNBytes(b, off, len);
	}

	@Override
	public byte[] readNBytes(int len) throws IOException {
		return this.wrapped.readNBytes(len);
	}

	@Override
	public void close() throws IOException {
		this.wrapped.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		this.wrapped.mark(readlimit);
	}

	@Override
	public synchronized void reset() throws IOException {
		this.wrapped.reset();
	}

	@Override
	public boolean markSupported() {
		return this.wrapped.markSupported();
	}

}

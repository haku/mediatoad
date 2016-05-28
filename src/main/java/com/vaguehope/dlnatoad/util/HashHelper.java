package com.vaguehope.dlnatoad.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashHelper {

	private HashHelper () {
		throw new AssertionError();
	}

	public static String sha1(final String s) {
		final MessageDigest dig = getSha1Digest();
		final byte[] bytes = dig.digest(getBytes(s));
		return new BigInteger(1, bytes).toString(16); // NOSONAR Hex is not a magic number.
	}

	private static byte[] getBytes (final String s) {
		try {
			return s.getBytes("UTF-8");
		}
		catch (final UnsupportedEncodingException e) {
			throw new IllegalStateException("JVM should always know about UTF-8.", e);
		}
	}

	private static MessageDigest getSha1Digest () {
		try {
			return MessageDigest.getInstance("SHA1");
		}
		catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException("JVM should always know about SHA1.", e);
		}
	}

	public static ThreadLocal<MessageDigest> mdMd5Factory = new ThreadLocal<MessageDigest>() {
		@Override
		protected MessageDigest initialValue () {
			try {
				return MessageDigest.getInstance("MD5");
			}
			catch (final NoSuchAlgorithmException e) {
				throw new IllegalStateException("JVM should always know about MD5.", e);
			}
		}
	};

	private static final int BUFFERSIZE = 1024 * 64; // 64kb.

	public static ByteBuffer createByteBuffer () {
		return ByteBuffer.allocateDirect(BUFFERSIZE);
	}

	public static BigInteger md5 (final File file) throws IOException {
		return md5(file, createByteBuffer());
	}

	public static BigInteger md5 (final File file, final ByteBuffer buffer) throws IOException {
		final MessageDigest md = mdMd5Factory.get();
		md.reset();

		final FileInputStream is;
		try {
			is = new FileInputStream(file);
		}
		catch (final FileNotFoundException e) {
			throw new FileNotFoundException("Not found: " + file.getAbsolutePath());
		}
		try {
			final FileChannel fc = is.getChannel();
			while (fc.position() < fc.size()) {
				buffer.clear();
				fc.read(buffer);
				buffer.flip();
				md.update(buffer);
			}
			return new BigInteger(1, md.digest());
		}
		finally {
			is.close();
		}
	}


}

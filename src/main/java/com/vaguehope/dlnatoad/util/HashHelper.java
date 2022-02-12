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

	private static final int BUFFERSIZE = 1024 * 64;

	private HashHelper () {
		throw new AssertionError();
	}

	public static String sha1(final String s) {
		final MessageDigest md = MD_SHA1_FACTORY.get();
		final byte[] bytes = md.digest(getBytes(s));
		return new BigInteger(1, bytes).toString(16); // NOSONAR Hex is not a magic number.
	}

	public static BigInteger sha1 (final File file) throws IOException {
		return sha1(file, createByteBuffer());
	}

	public static BigInteger sha1 (final File file, final ByteBuffer buffer) throws IOException {
		return hashFile(file, buffer, MD_SHA1_FACTORY);
	}

	public static String md5 (final String text) {
		final MessageDigest md = MD_MD5_FACTORY.get();
		md.update(text.getBytes(), 0, text.length());
		return new BigInteger(1, md.digest()).toString(16);
	}

	private static byte[] getBytes (final String s) {
		try {
			return s.getBytes("UTF-8");
		}
		catch (final UnsupportedEncodingException e) {
			throw new IllegalStateException("JVM should always know about UTF-8.", e);
		}
	}

	private static ByteBuffer createByteBuffer () {
		return ByteBuffer.allocateDirect(BUFFERSIZE);
	}

	private static final ThreadLocal<MessageDigest> MD_SHA1_FACTORY = new ThreadLocal<MessageDigest>() {
		@Override
		protected MessageDigest initialValue () {
			try {
				return MessageDigest.getInstance("SHA1");
			}
			catch (final NoSuchAlgorithmException e) {
				throw new IllegalStateException("JVM should always know about SHA1.", e);
			}
		}

		@Override
		public MessageDigest get () {
			final MessageDigest md = super.get();
			md.reset();
			return md;
		}
	};

	private static ThreadLocal<MessageDigest> MD_MD5_FACTORY = new ThreadLocal<MessageDigest>() {
		@Override
		protected MessageDigest initialValue () {
			try {
				return MessageDigest.getInstance("MD5");
			}
			catch (final NoSuchAlgorithmException e) {
				throw new IllegalStateException("JVM should always know about MD5.", e);
			}
		}

		@Override
		public MessageDigest get () {
			final MessageDigest md = super.get();
			md.reset();
			return md;
		}
	};

	private static BigInteger hashFile (final File file, final ByteBuffer buffer, final ThreadLocal<MessageDigest> mdFactory) throws FileNotFoundException, IOException {
		final MessageDigest md = mdFactory.get();
		md.reset();

		try (final FileInputStream is = new FileInputStream(file)) {
			try (final FileChannel fc = is.getChannel()) {
				while (fc.position() < fc.size()) {
					buffer.clear();
					fc.read(buffer);
					buffer.flip();
					md.update(buffer);
				}
				return new BigInteger(1, md.digest());
			}
		}
		catch (final FileNotFoundException e) {
			throw new FileNotFoundException("Not found: " + file.getAbsolutePath());
		}
	}

}

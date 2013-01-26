package com.vaguehope.dlnatoad.util;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashHelper {

	private HashHelper () {
		throw new AssertionError();
	}

	public static String sha1(String s) {
		MessageDigest dig = getSha1Digest();
		byte[] bytes = dig.digest(getBytes(s));
		return new BigInteger(1, bytes).toString(16); // NOSONAR Hex is not a magic number.
	}

	private static byte[] getBytes (String s) {
		try {
			return s.getBytes("UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException("JVM should always know about UTF-8.", e);
		}
	}

	private static MessageDigest getSha1Digest () {
		try {
			return MessageDigest.getInstance("SHA1");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("JVM should always know about SHA1.", e);
		}
	}

}

package com.vaguehope.dlnatoad.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashHelper {

	private HashHelper () {
		throw new AssertionError();
	}

	public static String sha1(String s) {
		MessageDigest dig = getSha1Digest();
		byte[] bytes = dig.digest(s.getBytes());
		return new BigInteger(1, bytes).toString(16);
	}

	private static MessageDigest getSha1Digest () {
		try {
			return MessageDigest.getInstance("SHA1");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}

package com.vaguehope.dlnatoad.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.util.HashHelper.Md5AndSha1;

public class HashHelperTest {

	private static final String FOOBAR_MD5 = "3858f62230ac3c915f300c664312c63f";
	private static final String FOOBAR_SHA1 = "8843d7f92416211de9ebb963ff4ce28125932878";
	private static final String FOOBAR_100000_MD5 = "f8d9ee8e3de45e512b79e4cbc0c2f83";
	private static final String FOOBAR_100000_SHA1 = "64698dc1315d0a665e7072bc248693d9fe384d7d";

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itSha1AFile() throws Exception {
		File file1 = this.tmp.newFile("file_1");
		FileUtils.writeStringToFile(file1, "1234567890abcdef", Charset.forName("UTF-8"));
		final BigInteger actual = HashHelper.sha1(file1);
		assertEquals(new BigInteger("ff998abc1ce6d8f01a675fa197368e44c8916e9c", 16), actual);
	}

	@Test
	public void itMd5AString() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertEquals(FOOBAR_MD5, HashHelper.md5("foobar").toString(16));
		}
	}

	@Test
	public void itMd5AFile() throws Exception {
		final File f = this.tmp.newFile();
		FileUtils.writeStringToFile(f, "foobar", StandardCharsets.UTF_8);
		final BigInteger actual = HashHelper.md5(f, HashHelper.createByteBuffer());
		assertEquals(FOOBAR_MD5, actual.toString(16));
	}

	@Test
	public void itMd5AndSha1AFile() throws Exception {
		final File f = this.tmp.newFile();
		FileUtils.writeStringToFile(f, "foobar", StandardCharsets.UTF_8);
		final Md5AndSha1 actual = HashHelper.generateMd5AndSha1(f, HashHelper.createByteBuffer());
		assertEquals(FOOBAR_MD5, actual.getMd5().toString(16));
		assertEquals(FOOBAR_SHA1, actual.getSha1().toString(16));
	}

	@Test
	public void itMd5AndSha1ALongerFile() throws Exception {
		final File f = this.tmp.newFile();
		final byte[] foobarBytes = "foobar".getBytes(StandardCharsets.UTF_8);
		try (OutputStream s = new FileOutputStream(f)) {
			for (int i = 0; i < 100000; i++) {
				s.write(foobarBytes);
			}
		}
		assertThat((int) f.length(), greaterThan(HashHelper.BUFFERSIZE));

		final Md5AndSha1 actual = HashHelper.generateMd5AndSha1(f, HashHelper.createByteBuffer());
		assertEquals(FOOBAR_100000_MD5, actual.getMd5().toString(16));
		assertEquals(FOOBAR_100000_SHA1, actual.getSha1().toString(16));
	}

}

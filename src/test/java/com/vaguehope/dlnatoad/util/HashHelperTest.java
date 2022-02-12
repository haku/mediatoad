package com.vaguehope.dlnatoad.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HashHelperTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itHashesAFile() throws Exception {
		File file1 = this.tmp.newFile("file_1");
		FileUtils.writeStringToFile(file1, "1234567890abcdef", Charset.forName("UTF-8"));
		final BigInteger actual = HashHelper.sha1(file1);
		assertEquals(new BigInteger("ff998abc1ce6d8f01a675fa197368e44c8916e9c", 16), actual);
	}

}

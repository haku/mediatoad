package com.vaguehope.dlnatoad.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ImageResizerTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ImageResizer undertest;

	@Before
	public void before() throws Exception {
		this.undertest = new ImageResizer(this.tmp.newFolder());
	}

	@Test
	public void itDecodesImage() throws Exception {
		final File in = this.tmp.newFile();
		try (final InputStream is = ImageResizer.class.getResourceAsStream("/icon.png")) {
			FileUtils.copyInputStreamToFile(is, in);
		}
		final File f = this.undertest.resizeFile(in, 24, 0.8f);
		assertTrue(f.exists());
		assertThat(f.length(), greaterThan(1L));
	}

}

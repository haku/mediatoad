package com.vaguehope.dlnatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MediaFileTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@SuppressWarnings("resource")
	@Test
	public void itRepresentsAZipArchive() throws Exception {
		final File archive = this.tmp.newFile("archive1.zip");
		try (final ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(archive))) {
			zo.putNextEntry(new ZipEntry("dir1/file0.jpeg"));
			IOUtils.write("test jpeg content", zo, StandardCharsets.UTF_8);
			zo.closeEntry();

			zo.putNextEntry(new ZipEntry("dir1/file1.mp3"));
			IOUtils.write("test mp3 content", zo, StandardCharsets.UTF_8);
			zo.closeEntry();
		}

		final List<MediaFile> mediaFiles = new ArrayList<>();
		try (final ZipInputStream zi = new ZipInputStream(new FileInputStream(archive))) {
			ZipEntry e;
			while ((e = zi.getNextEntry()) != null) {
				final ByteArrayOutputStream content = new ByteArrayOutputStream();
				IOUtils.copy(zi, content);
				mediaFiles.add(MediaFile.forZip(archive, e));
			}
		}

		assertThat(mediaFiles, hasSize(2));

		MediaFile f0 = mediaFiles.get(0);
		assertTrue(f0.exists());
		assertEquals("dir1/file0.jpeg", f0.name());
		assertEquals(17, f0.length());
		assertEquals("test jpeg content", IOUtils.toString(f0.open(), StandardCharsets.UTF_8));

		MediaFile f1 = mediaFiles.get(1);
		assertTrue(f1.exists());
		assertEquals("dir1/file1.mp3", f1.name());
		assertEquals(16, f1.length());
		assertEquals("test mp3 content", IOUtils.toString(f1.open(), StandardCharsets.UTF_8));
	}

}

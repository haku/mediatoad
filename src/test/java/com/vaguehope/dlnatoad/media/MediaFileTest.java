package com.vaguehope.dlnatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.fs.MediaFile;

public class MediaFileTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@SuppressWarnings("resource")
	@Test
	public void itRepresentsAZipArchive() throws Exception {
		final File archive = this.tmp.newFile("archive1.zip");
		try (final ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(archive))) {
			zo.putNextEntry(new ZipEntry("root.jpeg"));
			IOUtils.write("root content", zo, StandardCharsets.UTF_8);
			zo.closeEntry();

			zo.putNextEntry(new ZipEntry("dir1/file0.jpeg"));
			IOUtils.write("test jpeg content", zo, StandardCharsets.UTF_8);
			zo.closeEntry();

			zo.putNextEntry(new ZipEntry("dir1/file1.mp3"));
			IOUtils.write("test mp3 content", zo, StandardCharsets.UTF_8);
			zo.closeEntry();
		}

		final List<MediaFile> mediaFiles = MediaFile.expandZip(archive);
		assertThat(mediaFiles, hasSize(3));

		final MediaFile f0 = mediaFiles.get(0);
		assertTrue(f0.exists());
		assertEquals("root.jpeg", f0.getName());
		assertEquals(12, f0.length());
		assertEquals("root content", IOUtils.toString(f0.open(), StandardCharsets.UTF_8));

		final MediaFile f1 = mediaFiles.get(1);
		assertTrue(f1.exists());
		assertEquals("file0.jpeg", f1.getName());
		assertEquals(17, f1.length());
		assertEquals("test jpeg content", IOUtils.toString(f1.open(), StandardCharsets.UTF_8));

		final MediaFile f2 = mediaFiles.get(2);
		assertTrue(f2.exists());
		assertEquals("file1.mp3", f2.getName());
		assertEquals(16, f2.length());
		assertEquals("test mp3 content", IOUtils.toString(f2.open(), StandardCharsets.UTF_8));


	}

}

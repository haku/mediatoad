package com.vaguehope.dlnatoad.media;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.fs.MediaFile;

public class CoverArtHelperTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private File mediaFile;

	@Before
	public void before () throws Exception {
		this.mediaFile = this.tmp.newFile("testItem.ogg");
	}

	@Test
	public void itReturnsNullForNoMatch () throws Exception {
		testFindCoverArt(this.mediaFile, null);
	}

	@Test
	public void itReturnsNullWhenNoBetterOptions () throws Exception {
		givenNoise();
		testFindCoverArt(this.mediaFile, null);
	}

	@Test
	public void itFindsCoverForDirectory () throws Exception {
		givenNoise();
		final File cover = this.tmp.newFile("cover.jpg");
		testFindCoverArt(this.tmp.getRoot(), cover);
	}

	@Test
	public void itFindsCoverWithSameNameButJpgExt () throws Exception {
		givenCoverNoise();
		this.tmp.newFile("testItem thumb.jpg");
		final File cover = this.tmp.newFile("testItem.jpg");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverWithSameNameButJpgExtMixedCase () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem.jpG");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverWithSameNameButMixedCaseAndJpgExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("TestItem.jpg");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverWithSameNameStartAndJpgExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem thumb.jpg");
		testFindCoverArt(this.mediaFile, cover);
	}

	/**
	 * e.g.:
	 * Album - Track.ogg
	 * Album.jpg
	 */
	@Test
	public void itFindsCoverWithShortenedNameStartAndJpgExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testi.jpg");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverWithSameNameButJpegExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem.jpeg");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverWithSameNameButGifExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem.gif");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverWithSameNameButPngExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem.png");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverCalledCover () throws Exception {
		givenNoise();
		final File cover = this.tmp.newFile("cover.jpg");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverCalledCoverMixedCase () throws Exception {
		givenNoise();
		final File cover = this.tmp.newFile("coVeR.jpg");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverCalledAlbum () throws Exception {
		givenNoise();
		final File cover = this.tmp.newFile("album.jpg");
		testFindCoverArt(this.mediaFile, cover);
	}

	@Test
	public void itFindsCoverCalledFolderOverCover () throws Exception {
		givenNoise();
		this.tmp.newFile("cover.jpg");
		this.tmp.newFile("album.jpg");
		final File cover = this.tmp.newFile("folder.jpg");
		testFindCoverArt(this.mediaFile, cover);
	}

	public static void testFindCoverArt (final File input, final File expectedOutput) throws IOException {
		final MediaFile actual = CoverArtHelper.findCoverArt(MediaFile.forFile(input));
		if (expectedOutput == null) {
			assertEquals(null, actual);
		}
		else {
			assertEquals(expectedOutput.getAbsolutePath(), actual.getAbsolutePath());
		}
	}

	private void givenCoverNoise () throws IOException {
		givenNoise();
		for (final String ext : new String[] { "jpg", "png", "gif" }) {
			for (final String name : new String[] { "cover", "album", "folder" }) {
				this.tmp.newFile(name + "." + ext);
			}
		}
	}

	private void givenNoise () throws IOException {
		this.tmp.newFile("something.jpg");
	}

}

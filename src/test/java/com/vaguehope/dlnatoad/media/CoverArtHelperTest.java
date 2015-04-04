package com.vaguehope.dlnatoad.media;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CoverArtHelperTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private File mediaFile;

	@Before
	public void before () throws Exception {
		this.mediaFile = this.tmp.newFile("testItem.ogg");
	}

	@Test
	public void itReturnsNullForNoMatch () throws Exception {
		assertEquals(null, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itReturnsNullWhenNoBetterOptions () throws Exception {
		givenNoise();
		assertEquals(null, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverForDirectory () throws Exception {
		givenNoise();
		final File cover = this.tmp.newFile("cover.jpg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.tmp.getRoot()));
	}

	@Test
	public void itFindsCoverWithSameNameButJpgExt () throws Exception {
		givenCoverNoise();
		this.tmp.newFile("testItem thumb.jpg");
		final File cover = this.tmp.newFile("testItem.jpg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverWithSameNameButJpgExtMixedCase () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem.jpG");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverWithSameNameButMixedCaseAndJpgExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("TestItem.jpg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverWithSameNameStartAndJpgExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem thumb.jpg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
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
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverWithSameNameButJpegExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem.jpeg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverWithSameNameButGifExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem.gif");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverWithSameNameButPngExt () throws Exception {
		givenCoverNoise();
		final File cover = this.tmp.newFile("testItem.png");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverCalledCover () throws Exception {
		givenNoise();
		final File cover = this.tmp.newFile("cover.jpg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverCalledCoverMixedCase () throws Exception {
		givenNoise();
		final File cover = this.tmp.newFile("coVeR.jpg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverCalledAlbum () throws Exception {
		givenNoise();
		final File cover = this.tmp.newFile("album.jpg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
	}

	@Test
	public void itFindsCoverCalledFolderOverCover () throws Exception {
		givenNoise();
		this.tmp.newFile("cover.jpg");
		this.tmp.newFile("album.jpg");
		final File cover = this.tmp.newFile("folder.jpg");
		assertEquals(cover, CoverArtHelper.findCoverArt(this.mediaFile));
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

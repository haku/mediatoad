package com.vaguehope.dlnatoad.media;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;

import com.vaguehope.dlnatoad.media.MetadataReader.Metadata;


public class MetadataReaderTest {

	@Test
	public void itReadsFilenameArtistAlbum () throws Exception {
		testFileNameExtraction("/foo/bar/Some Artist - Some Album - 01 - Some Track.ogg",
				"Some Artist", "Some Album");
	}

	@Test
	public void itReadsArtistFilename () throws Exception {
		testFileNameExtraction("/foo/bar/Some Artist - Some Track.ogg",
				"Some Artist", null);
	}

	@Ignore
	@Test
	public void itReadsArtistAlbumPathAndFilename () throws Exception {
		testFileNameExtraction("/foo/bar/Some Artist - Some Album/01 - Some Track.ogg",
				"Some Artist", "Some Album");
	}

	@Ignore
	@Test
	public void itReadsArtistPathAndFilename () throws Exception {
		testFileNameExtraction("/foo/bar/Some Artist/01 - Some Track.ogg",
				"Some Artist", null);
	}

	private static void testFileNameExtraction(final String path, final String artist, final String album) {
		assertEquals(new Metadata(artist, album),
				MetadataReader.read(new File(path)));
	}

}

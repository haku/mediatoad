package mediatoad.media;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import mediatoad.media.MetadataReader.Confidence;
import mediatoad.media.MetadataReader.Metadata;

@RunWith(Parameterized.class)
public class MetadataReaderTest {

	private static final Metadata ARTIST_ALBUM_TITLE = new Metadata("Some Artist", "Some Album", "Some Track", Confidence.OK);
	private static final Metadata ARTIST_TITLE = new Metadata("Some Artist", null, "Some Track", Confidence.OK);
	private static final Metadata ALBUM_TITLE = new Metadata(null, "Some Album", "Some Track", Confidence.OK);

	@Parameters(name = "{index}: {1} = {0}")
	public static Collection<Object[]> params() {
		return Arrays.asList(new Object[][] {
				// Basic structure
				{ ARTIST_ALBUM_TITLE, "/foo/bar/Some Artist - Some Album - 01 - Some Track.ogg" },
				{ ARTIST_ALBUM_TITLE, "/foo/bar/Some Artist - Some Album/01 - Some Track.ogg" },
				{ ARTIST_ALBUM_TITLE, "/foo/bar/Some Artist - Some Album/Some Track.ogg" },
				{ ARTIST_TITLE, "/foo/bar/Some Artist - Some Track.ogg" },
				{ ARTIST_TITLE.withConfidence(Confidence.GUESS), "/foo/bar/Some Artist/Some Track.ogg" },
				{ ALBUM_TITLE, "/foo/bar/Some Album/01 - Some Track.ogg" },

				// Awkward titles
				{ ARTIST_ALBUM_TITLE.withTitle("Some Track - Some Subtitle"),
						"/foo/bar/Some Artist - Some Album - 01 - Some Track - Some Subtitle.ogg" },
				{ ARTIST_ALBUM_TITLE.withTitle("Some Track - 99 - Some Subtitle"),
						"/foo/bar/Some Artist - Some Album - 01 - Some Track - 99 - Some Subtitle.ogg" },
				{ ARTIST_ALBUM_TITLE.withTitle("Some Track - Some Subtitle"),
						"/foo/bar/Some Artist - Some Album/01 - Some Track - Some Subtitle.ogg" },
				{ ARTIST_ALBUM_TITLE.withTitle("Some Track - 99 - Some Subtitle"),
						"/foo/bar/Some Artist - Some Album/01 - Some Track - 99 - Some Subtitle.ogg" },

				// Remove notes in [brackets] [after whitespace]
				{ ARTIST_ALBUM_TITLE, "/foo/bar/Some Artist - Some Album - 01 - Some Track [some note].ogg" },
				{ ARTIST_ALBUM_TITLE, "/foo/bar/Some Artist - Some Album/01 - Some Track [some note] [another].ogg" },
				{ ARTIST_ALBUM_TITLE, "/foo/bar/Some Artist - Some Album/Some Track [some note] [another].ogg" },
				{ ARTIST_TITLE, "/foo/bar/Some Artist - Some Track [some note]  [another].ogg" },
				{ ARTIST_TITLE.withConfidence(Confidence.GUESS), "/foo/bar/Some Artist/Some Track [some note]  [another].ogg" },
				{ ALBUM_TITLE, "/foo/bar/Some Album/01 - Some Track [some note]  [another].ogg" },
		});
	}

	@Parameter(0)
	public Metadata expected;

	@Parameter(1)
	public String filePath;

	@Test
	public void itExtractsMetadata() throws Exception {
		assertEquals(this.expected, MetadataReader.read(new File(this.filePath)));
	}

}

package com.vaguehope.dlnatoad.dlnaserver;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vaguehope.dlnatoad.dlnaserver.SearchEngine.Predicate;
import com.vaguehope.dlnatoad.dlnaserver.SearchEngine.Where;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MockContent;

public class SearchEngineTest {

	private ContentTree contentTree;
	private MockContent mockContent;
	private SearchEngine undertest;

	@Before
	public void before () throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree);
		this.undertest = new SearchEngine();
	}

	@Test
	public void itSearchesByTitle () throws Exception {
		final List<ContentItem> items = this.mockContent.givenMockItems(MediaFormat.MP4, 10);
		when(items.get(3).getTitle()).thenReturn("some file foo\"Bar song.mp4");

		final List<ContentItem> ret = this.undertest.search(this.contentTree.getRootNode(),
				"(upnp:class derivedfrom \"object.item.videoItem\" and dc:title contains \"foo\\\"bar\")",
				10);

		assertEquals(items.subList(3, 4), ret);
	}

	@Test
	public void itLimitsResults () throws Exception {
		final List<ContentItem> items = this.mockContent.givenMockItems(MediaFormat.MP4, 10);
		for (final ContentItem cn : items) {
			when(cn.getTitle()).thenReturn("some file foo\"Bar song.mp4");
		}

		final List<ContentItem> ret = this.undertest.search(this.contentTree.getRootNode(),
				"(upnp:class derivedfrom \"object.item.videoItem\" and dc:title contains \"foo\\\"bar\")",
				5);

		assertEquals(5, ret.size());
	}

	@Test
	public void itParsesVideoWithTitle () throws Exception {
		final Predicate<ContentItem> p = SearchEngine.criteriaToPredicate("(upnp:class derivedfrom \"object.item.videoItem\" and dc:title contains \"daa\")");
		assertThat(p, hasToString("(contentGroupIs VIDEO and titleContains 'daa')"));
		assertWhere(p, "(TRUE AND file LIKE ? ESCAPE ?)", "daa", "\\");
	}

	@Test
	public void itParsesAudioWithTitle () throws Exception {
		final Predicate<ContentItem> p = SearchEngine.criteriaToPredicate("(upnp:class derivedfrom \"object.item.audioItem\" and dc:title contains \"daa\")");
		assertThat(p, hasToString("(contentGroupIs AUDIO and titleContains 'daa')"));
		assertWhere(p, "(TRUE AND file LIKE ? ESCAPE ?)", "daa", "\\");
	}

	@Test
	public void itParsesAudioWithCreatorOrArtist () throws Exception {
		final Predicate<ContentItem> p = SearchEngine.criteriaToPredicate("(upnp:class derivedfrom \"object.item.audioItem\" and (dc:creator contains \"daa\" or upnp:artist contains \"daa\"))");
		assertThat(p, hasToString("(contentGroupIs AUDIO and (artistContains 'daa' or artistContains 'daa'))"));
		assertWhere(p, "(TRUE AND (file LIKE ? ESCAPE ? OR file LIKE ? ESCAPE ?))", "daa", "\\", "daa", "\\");
	}

	@Test
	public void itParsesAudioWithAlbumAndTitle () throws Exception {
		// Note: album currently ignored.
		final Predicate<ContentItem> p = SearchEngine.criteriaToPredicate("(upnp:class = \"object.container.album.musicAlbum\" and dc:title contains \"daa\")");
		assertThat(p, hasToString("(TRUE and titleContains 'daa')"));
		assertWhere(p, "(TRUE AND file LIKE ? ESCAPE ?)", "daa", "\\");
	}

	@Test
	public void itParsesAudioWithArtistAndTitle () throws Exception {
		// Note: person currently ignored.
		final Predicate<ContentItem> p = SearchEngine.criteriaToPredicate("(upnp:class = \"object.container.person.musicArtist\" and dc:title contains \"daa\")");
		assertThat(p, hasToString("(TRUE and titleContains 'daa')"));
		assertWhere(p, "(TRUE AND file LIKE ? ESCAPE ?)", "daa", "\\");
	}

	@Test
	public void itParsesVideoOrAudioWithTitle () throws Exception {
		final Predicate<ContentItem> p = SearchEngine.criteriaToPredicate("((upnp:class derivedfrom \"object.item.videoItem\" or upnp:class derivedfrom \"object.item.audioItem\") and dc:title contains \"foo\")");
		assertThat(p, hasToString("((contentGroupIs VIDEO or contentGroupIs AUDIO) and titleContains 'foo')"));
		assertWhere(p, "((TRUE OR TRUE) AND file LIKE ? ESCAPE ?)", "foo", "\\");
	}

	@Test
	public void itParsesTitleOrCreatorOrArtist () throws Exception {
		final Predicate<ContentItem> p = SearchEngine.criteriaToPredicate("(dc:title contains \"foo\" or dc:creator contains \"daa\" or upnp:artist contains \"daa\")");
		assertThat(p, hasToString("((titleContains 'foo' or artistContains 'daa') or artistContains 'daa')"));
		assertWhere(p, "((file LIKE ? ESCAPE ? OR file LIKE ? ESCAPE ?) OR file LIKE ? ESCAPE ?)", "foo", "\\", "daa", "\\", "daa", "\\");
	}

	private static void assertWhere(final Predicate<ContentItem> p, final String clause, final String... params) {
		final Where w = p.getWhere();
		assertEquals(clause, w.clause);
		assertEquals(Arrays.asList(params), w.params);
	}

}

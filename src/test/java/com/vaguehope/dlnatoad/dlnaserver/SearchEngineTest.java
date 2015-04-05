package com.vaguehope.dlnatoad.dlnaserver;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.teleal.cling.support.model.item.Item;
import org.teleal.cling.support.model.item.VideoItem;

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
		final List<ContentNode> items = this.mockContent.givenMockItems(VideoItem.class, 10);
		when(items.get(3).getItem().getTitle()).thenReturn("some file foo\"Bar song.mp4");

		final List<Item> ret = this.undertest.search(this.contentTree.getRootNode(),
				"(upnp:class derivedfrom \"object.item.videoItem\" and dc:title contains \"foo\\\"bar\")");

		assertEquals(MockContent.listOfItems(items.subList(3, 4)), ret);
	}

	@Test
	public void itParsesVideoWithTitle () throws Exception {
		assertThat(SearchEngine.criteriaToPredicate("(upnp:class derivedfrom \"object.item.videoItem\" and dc:title contains \"daa\")"),
				hasToString("instanceOf VideoItem and titleContains 'daa'"));
	}

	@Test
	public void itParsesAudioWithTitle () throws Exception {
		assertThat(SearchEngine.criteriaToPredicate("(upnp:class derivedfrom \"object.item.audioItem\" and dc:title contains \"daa\")"),
				hasToString("instanceOf AudioItem and titleContains 'daa'"));
	}

	@Test
	public void itParsesAudioWithCreatorOrArtist () throws Exception {
		assertThat(SearchEngine.criteriaToPredicate("(upnp:class derivedfrom \"object.item.audioItem\" and (dc:creator contains \"daa\" or upnp:artist contains \"daa\"))"),
				hasToString("instanceOf AudioItem and artistContains 'daa'"));
	}

	@Test
	public void itParsesAudioWithAlbumAndTitle () throws Exception {
		// Note: album currently ignored.
		assertThat(SearchEngine.criteriaToPredicate("(upnp:class = \"object.container.album.musicAlbum\" and dc:title contains \"daa\")"),
				hasToString("titleContains 'daa'"));
	}

	@Test
	public void itParsesAudioWithArtistAndTitle () throws Exception {
		// Note: person currently ignored.
		assertThat(SearchEngine.criteriaToPredicate("(upnp:class = \"object.container.person.musicArtist\" and dc:title contains \"daa\")"),
				hasToString("titleContains 'daa'"));
	}

}

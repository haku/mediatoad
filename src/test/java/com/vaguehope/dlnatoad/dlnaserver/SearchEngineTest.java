package com.vaguehope.dlnatoad.dlnaserver;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.teleal.cling.support.model.item.Item;

public class SearchEngineTest {

	private ContentTree contentTree;
	private MockContent mockContent;
	private SearchEngine undertest;

	/*
	 * (upnp:class derivedfrom "object.item.videoItem" and dc:title contains "daa")
	 * (upnp:class derivedfrom "object.item.audioItem" and dc:title contains "daa")
	 * (upnp:class derivedfrom "object.item.audioItem" and (dc:creator contains "daa" or upnp:artist contains "daa"))
	 * (upnp:class = "object.container.album.musicAlbum" and dc:title contains "daa")
	 * (upnp:class = "object.container.person.musicArtist" and dc:title contains "daa")
	 */

	@Before
	public void before () throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree);
		this.undertest = new SearchEngine();
	}

	@Test
	public void itSearchesByTitle () throws Exception {
		final List<ContentNode> items = this.mockContent.givenMockItems(10);
		when(items.get(3).getItem().getTitle()).thenReturn("some file foo\"Bar song.mp4");

		final List<Item> ret = this.undertest.search(this.contentTree.getRootNode(),
				"(upnp:class derivedfrom \"object.item.videoItem\" and dc:title contains \"foo\\\"bar\")");

		assertEquals(MockContent.listOfItems(items.subList(3, 4)), ret);
	}

	@Ignore("not implemented")
	@Test
	public void itSearchesByClass () throws Exception {
		// TODO
	}

}

package com.vaguehope.dlnatoad.dlnaserver;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.DIDLObject.Property;
import org.fourthline.cling.support.model.PersonWithRole;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.AudioItem;
import org.fourthline.cling.support.model.item.Item;
import org.junit.Before;
import org.junit.Test;

import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ExternalUrls;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MetadataReader.Metadata;

public class NodeConverterTest {

	private static final String EXTERNAL_HTTP_CONTEXT = "http://foo:123";

	private NodeConverter undertest;

	@Before
	public void before() throws Exception {
		this.undertest = new NodeConverter(new ExternalUrls(EXTERNAL_HTTP_CONTEXT));
	}

	@Test
	public void itMakesAContainer() throws Exception {
		final ContentNode input = new ContentNode("id", "pid", "title", null);
		input.addNodeIfAbsent(new ContentNode("0", "id", "0", null));
		input.addNodeIfAbsent(new ContentNode("1", "id", "1", null));
		input.addItemIfAbsent(new ContentItem("2", "id", "2", null, MediaFormat.AAC));
		input.addItemIfAbsent(new ContentItem("3", "id", "3", null, MediaFormat.AAC));

		final File artFile = mock(File.class);
		when(artFile.exists()).thenReturn(true);
		when(artFile.length()).thenReturn(222L);
		final ContentItem art = new ContentItem("art", null, null, artFile, MediaFormat.JPEG);
		input.setArt(art);

		final Container c = this.undertest.makeContainerWithoutSubContainers(input);
		assertEquals("id", c.getId());
		assertEquals("pid", c.getParentID());
		assertEquals("title", c.getTitle());
		assertEquals(Integer.valueOf(4), c.getChildCount());

		final Property<?> artProp = c.getProperties().get(0);
		assertThat(artProp, instanceOf(DIDLObject.Property.UPNP.ALBUM_ART_URI.class));
		assertEquals(URI.create("http://foo:123/c/art"), artProp.getValue());

		assertThat(c.getContainers(), hasSize(0));
		assertThat(c.getItems(), hasSize(0));
	}

	@Test
	public void itDoesNotMakeContainersWithAuthLists() throws Exception {
		final ContentNode input = new ContentNode("id", "pid", "title", null);
		input.addNodeIfAbsent(new ContentNode("0", "id", "0", null, mock(AuthList.class), null));
		input.addNodeIfAbsent(new ContentNode("1", "id", "1", null));

		final List<Container> actual = this.undertest.makeSubContainersWithoutTheirSubContainers(input);
		assertEquals("1", actual.get(0).getId());
		assertThat(actual, hasSize(1));
	}

	@Test
	public void itMakesAnItem() throws Exception {
		final File inputFile = mock(File.class);
		when(inputFile.exists()).thenReturn(true);
		when(inputFile.length()).thenReturn(123456L);
		final ContentItem input = new ContentItem("id", "pid", "title", inputFile, MediaFormat.MP3);

		final Metadata md = new Metadata("artist", "album");
		input.setMetadata(md);
		input.setDurationMillis(TimeUnit.MINUTES.toMillis(90));

		final File artFile = mock(File.class);
		when(artFile.exists()).thenReturn(true);
		when(artFile.length()).thenReturn(222L);
		final ContentItem art = new ContentItem("art", null, null, artFile, MediaFormat.JPEG);
		input.setArt(art);

		final File subtitlesFile = mock(File.class);
		when(subtitlesFile.exists()).thenReturn(true);
		when(subtitlesFile.length()).thenReturn(111L);
		final ContentItem subtitles = new ContentItem("subtitles", null, null, subtitlesFile, MediaFormat.SRT);
		input.addAttachmentIfNotPresent(subtitles);

		final Item i = this.undertest.makeItem(input);
		assertThat(i, instanceOf(AudioItem.class));
		assertEquals("id", i.getId());
		assertEquals("pid", i.getParentID());
		assertEquals("title", i.getTitle());

		final Res res = i.getResources().get(0);
		assertEquals("http-get:*:audio/mpeg:*", res.getProtocolInfo().toString());
		assertEquals(Long.valueOf(123456L), res.getSize());
		assertEquals("http://foo:123/c/id", res.getValue());
		assertEquals("01:30:00", res.getDuration());

		final Property<?> artProp = i.getProperties().get(0);
		assertThat(artProp, instanceOf(DIDLObject.Property.UPNP.ALBUM_ART_URI.class));
		assertEquals(URI.create("http://foo:123/c/art"), artProp.getValue());

		final Res artRes = i.getResources().get(1);
		assertEquals(
				"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN;DLNA.ORG_OP=01;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=00d00000000000000000000000000000",
				artRes.getProtocolInfo().toString());
		assertEquals(Long.valueOf(222L), artRes.getSize());

		final Res subtitlesRes = i.getResources().get(2);
		assertEquals("http-get:*:text/srt:*", subtitlesRes.getProtocolInfo().toString());
		assertEquals(Long.valueOf(111L), subtitlesRes.getSize());
		assertEquals("http://foo:123/c/subtitles", subtitlesRes.getValue());

		final Property<?> artistProp = i.getProperties().get(1);
		assertThat(artistProp, instanceOf(DIDLObject.Property.UPNP.ARTIST.class));
		assertEquals("artist", ((PersonWithRole) artistProp.getValue()).getName());

		final Property<?> albumProp = i.getProperties().get(2);
		assertThat(albumProp, instanceOf(DIDLObject.Property.UPNP.ALBUM.class));
		assertEquals("album", albumProp.getValue());
	}

}

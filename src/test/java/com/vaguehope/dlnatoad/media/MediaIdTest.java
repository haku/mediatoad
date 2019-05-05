package com.vaguehope.dlnatoad.media;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;

public class MediaIdTest {

	@Test
	public void itMakesTempId() throws Exception {
		final MediaId mid = new MediaId(null);
		assertEquals("video-62c72919722e071e58b289a83cd12a83443fd46a-MyVideo_mp4",
				mid.contentIdSync(ContentGroup.VIDEO, new File("MyVideo.mp4")));
	}

}

package com.vaguehope.dlnatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

public class MediaIdTest {

	private MediaId undertest;

	@Before
	public void before() throws Exception {
		this.undertest = new MediaId(null);
	}

	@Test
	public void itMakesTempId() throws Exception {
		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		this.undertest.contentIdAsync(ContentGroup.VIDEO, new File("MyVideo.mp4"), null, cb);
		assertThat(cb.getMediaId(), matchesPattern("video-[0-9a-f]+-MyVideo_mp4"));
	}

}

package com.vaguehope.dlnatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Test;

public class MediaIdTest {

	private ExecutorService exSvc;
	private MediaId undertest;

	@Before
	public void before() throws Exception {
		this.exSvc = mock(ExecutorService.class);
		this.undertest = new MediaId(null, this.exSvc);
	}

	@Test
	public void itMakesTempId() throws Exception {
		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		this.undertest.contentIdAsync(ContentGroup.VIDEO, new File("MyVideo.mp4"), cb);
		assertThat(cb.getMediaId(), matchesPattern("video-[0-9a-f]+-MyVideo_mp4"));
	}

}

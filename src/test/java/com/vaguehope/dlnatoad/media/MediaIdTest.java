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

	@Before
	public void before() throws Exception {
		this.exSvc = mock(ExecutorService.class);
	}

	@Test
	public void itMakesTempId() throws Exception {
		final MediaId mid = new MediaId(null, this.exSvc);
		String actual = mid.contentIdSync(ContentGroup.VIDEO, new File("MyVideo.mp4"), this.exSvc);
		assertThat(actual, matchesPattern("video-[0-9a-f]+-MyVideo_mp4"));
	}

}

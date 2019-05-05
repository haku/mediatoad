package com.vaguehope.dlnatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import java.io.File;

import org.junit.Test;

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;

public class MediaIdTest {

	@Test
	public void itMakesTempId() throws Exception {
		final MediaId mid = new MediaId(null);
		String actual = mid.contentIdSync(ContentGroup.VIDEO, new File("MyVideo.mp4"));
		assertThat(actual, matchesPattern("video-[0-9a-f]+-MyVideo_mp4"));
	}

}

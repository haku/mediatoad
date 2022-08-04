package com.vaguehope.dlnatoad.media;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.File;

import org.junit.Test;

import com.vaguehope.dlnatoad.auth.AuthList;

public class ContentNodeTest {

	@Test
	public void itDoesNotModifyTitleIfNoAuthList() throws Exception {
		final ContentNode n = new ContentNode("id", ContentGroup.AUDIO.getId(), "title", new File(""), null, null);
		assertEquals("title", n.getTitle());
	}

	@Test
	public void itAddsRestrictedToTitleIfAuthList() throws Exception {
		final AuthList authList = mock(AuthList.class);
		final ContentNode n = new ContentNode("id", ContentGroup.AUDIO.getId(), "title", new File(""), authList, null);
		assertEquals("title (restricted)", n.getTitle());
	}

}

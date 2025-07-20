package com.vaguehope.dlnatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vaguehope.dlnatoad.auth.AuthList;

public class RecentContentNodeTest {

	private ContentTree contentTree;
	private MockContent mockContent;
	private RecentContentNode undertest;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree);
		this.undertest = new RecentContentNode();
	}

	@Test
	public void itDoesSomething() throws Exception {
		final AuthList allowAuthlist = mock(AuthList.class);
		when(allowAuthlist.hasUser("shork")).thenReturn(true);

		final ContentNode node = this.mockContent.addMockDir("node", allowAuthlist);
		final ContentItem item = this.mockContent.addMockItem("item", node);

		this.undertest.maybeAddToRecent(item, node);

		final List<ContentItem> actual = this.undertest.itemsUserHasAuth("shork");
		assertThat(actual, contains(item));

		final List<ContentItem> actualNoAuth = this.undertest.itemsUserHasAuth(null);
		assertThat(actualNoAuth, hasSize(0));
	}

}

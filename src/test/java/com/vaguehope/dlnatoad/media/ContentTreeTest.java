package com.vaguehope.dlnatoad.media;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ContentTreeTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree undertest;
	private MockContent mockContent;

	@Before
	public void before() throws Exception {
		this.undertest = new ContentTree();
		this.mockContent = new MockContent(this.undertest, this.tmp);
	}

	@Test
	public void itHasDirs() throws Exception {
		this.mockContent.givenMockDirs(7);
		assertThat(this.undertest.getNodes(), hasSize(7 + 2));  // Extras for root and recent.
	}

	@Test
	public void itHasItems() throws Exception {
		this.mockContent.givenMockItems(10);
		assertThat(this.undertest.getItems(), hasSize(10));
	}

	@Test
	public void itRemovesEmptyDirs() throws Exception {
		ContentNode a = this.mockContent.addMockDir("aa");
		ContentNode b = this.mockContent.addMockDir("bb", a);
		ContentNode c = this.mockContent.addMockDir("cc", b);
		ContentNode d = this.mockContent.addMockDir("dd", c);

		this.undertest.removeFile(d.getFile());
		assertThat(this.undertest.getNodes(), not(hasItem(d)));
		assertThat(this.undertest.getNodes(), not(hasItem(c)));
		assertThat(this.undertest.getNodes(), not(hasItem(b)));
		assertThat(this.undertest.getNodes(), not(hasItem(a)));
	}

	@Test
	public void itMakesRecentContainer() throws Exception {
		final List<ContentItem> mockItems = this.mockContent.givenMockItems(300, sequentialTimeStamps());
		final List<ContentItem> expected = mockItems.subList(mockItems.size() - 200, mockItems.size());
		Collections.reverse(expected);

		final ContentNode cn = this.undertest.getNode(ContentGroup.RECENT.getId());
		assertThat(cn.getCopyOfItems(), equalTo(expected));
	}

	@Test
	public void itDoesNotAddThumbnailsToRecent() throws Exception {
		File thumbFile = mock(File.class);
		this.undertest.addItem(new ContentItem("thumbId", null, null, thumbFile, MediaFormat.JPEG));

		final ContentNode cn = this.undertest.getNode(ContentGroup.RECENT.getId());
		assertThat(cn.getCopyOfItems(), hasItems());
		assertEquals(this.undertest.getItemCount(), 1);
	}

	@Test
	public void itRemovesOldItemsFromRecent() throws Exception {
		final List<ContentItem> mockItems = this.mockContent.givenMockItems(300, sequentialTimeStamps());
		final List<ContentItem> expected = mockItems.subList(mockItems.size() - 200, mockItems.size());
		Collections.reverse(expected);

		final Collection<ContentItem> actual = this.undertest.getRecent();
		assertEquals(expected, new ArrayList<>(actual));
	}

	@Test
	public void itRemovesGoneItemsFromRecent() throws Exception {
		List<ContentItem> items = this.mockContent.givenMockItems(1);
		assertThat(this.undertest.getRecent(), hasSize(1));

		final File file = items.get(0).getFile();
		assertEquals(1, this.undertest.removeFile(file));

		assertThat(this.undertest.getRecent(), hasSize(0));
	}

	private static Consumer<File> sequentialTimeStamps() {
		return new Consumer<File>() {
			@Override
			public void accept(File f) {
				int n = Integer.parseInt(f.getName().substring(0, f.getName().indexOf(".")).replace("id", ""));
				when(f.lastModified()).thenReturn(n * 1000L);
			}
		};
	}

}

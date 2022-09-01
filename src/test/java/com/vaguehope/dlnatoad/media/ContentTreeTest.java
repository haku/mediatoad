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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.auth.AuthList;

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
		final ContentNode a = this.mockContent.addMockDir("aa");
		final ContentNode b = this.mockContent.addMockDir("bb", a);
		final ContentNode c = this.mockContent.addMockDir("cc", b);
		final ContentNode d = this.mockContent.addMockDir("dd", c);

		this.undertest.removeFile(d.getFile());
		assertThat(this.undertest.getNodes(), not(hasItem(d)));
		assertThat(this.undertest.getNodes(), not(hasItem(c)));
		assertThat(this.undertest.getNodes(), not(hasItem(b)));
		assertThat(this.undertest.getNodes(), not(hasItem(a)));
	}

	@Test
	public void itReturnsASetOfItems() throws Exception {
		final List<String> openItems = itemIds(this.mockContent.givenMockItems(100));

		final AuthList allowAuthlist = mock(AuthList.class);
		when(allowAuthlist.hasUser("shork")).thenReturn(true);
		final ContentNode allowProtecDir = this.mockContent.addMockDir("allow-dir-protec", this.undertest.getRootNode(), allowAuthlist);
		final List<String> allowProtecItems = itemIds(this.mockContent.givenMockItems(100, allowProtecDir));

		final AuthList blockAuthlist = mock(AuthList.class);
		final ContentNode blockProtecDir = this.mockContent.addMockDir("block-dir-protec", this.undertest.getRootNode(), blockAuthlist);
		final List<String> blockProtecItems = itemIds(this.mockContent.givenMockItems(100, blockProtecDir));

		final List<String> idsToFetch = new ArrayList<>();
		idsToFetch.addAll(openItems.subList(10, 20));
		idsToFetch.addAll(allowProtecItems.subList(10, 20));
		idsToFetch.addAll(blockProtecItems.subList(10, 20));

		final List<String> expectedIds = new ArrayList<>();
		expectedIds.addAll(openItems.subList(10, 20));
		expectedIds.addAll(allowProtecItems.subList(10, 20));

		final List<String> actualIds = itemIds(this.undertest.getItemsForIds(idsToFetch, "shork"));
		assertEquals(expectedIds, actualIds);
	}

	@Ignore("Micro benchmark for checking performance of sort on insert.")
	@Test
	public void itMapsIdsToItemsQuickly() throws Exception {
		final int nodeCount = 4000;
		final int itemCount = 50000;
		final int fetchCount = 500;  // max search results.
		final int testIterations = 100;

		final StopWatch setupTime = StopWatch.createStarted();
		this.mockContent = new MockContent(this.undertest);
		this.mockContent.setSpy(false);
		this.mockContent.setShuffle(false);

		final AuthList authList = AuthList.ofNames("foo", "bar", "bat", "baz", "shork");
		final int itemsPerNode = itemCount / nodeCount;
		for (int i = 0; i < nodeCount; i++) {
			final ContentNode n = this.mockContent.addMockDir("node-" + i, authList);
			this.mockContent.givenMockItems(itemsPerNode, n);
		}
		final int leftOver = itemCount - (itemsPerNode * nodeCount);
		this.mockContent.givenMockItems(leftOver, this.undertest.getNodes().iterator().next());
		assertEquals(itemCount, this.undertest.getItemCount());

		final List<String> allItemIds = itemIds(this.undertest.getItems());
		System.out.println("Setup time: " + setupTime.getTime(TimeUnit.MILLISECONDS) + "ms");

		long totalTime = 0L;
		for (int i = 0; i < testIterations; i++) {
			Collections.shuffle(allItemIds);
			final List<String> idsToFetch = allItemIds.subList(0, fetchCount);

			final long start = System.nanoTime();
			final List<ContentItem> ret = this.undertest.getItemsForIds(idsToFetch, "shork");
			final long end = System.nanoTime();
			totalTime += end - start;

			assertEquals(fetchCount, ret.size());
		}
		final double meanTime = (double) totalTime / (double) testIterations;
		System.out.println("Mapping " + fetchCount + " items average duration: " + meanTime + " nanos = " + TimeUnit.NANOSECONDS.toMillis((long) meanTime) + " ms");
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
		final File thumbFile = mock(File.class);
		this.undertest.addItem(new ContentItem("thumbId", "id-of-container", null, thumbFile, MediaFormat.JPEG));

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
		final List<ContentItem> items = this.mockContent.givenMockItems(1);
		assertThat(this.undertest.getRecent(), hasSize(1));

		final File file = items.get(0).getFile();
		assertEquals(1, this.undertest.removeFile(file));

		assertThat(this.undertest.getRecent(), hasSize(0));
	}

	private static Consumer<File> sequentialTimeStamps() {
		return new Consumer<File>() {
			@Override
			public void accept(final File f) {
				final int n = Integer.parseInt(f.getName().substring(0, f.getName().indexOf(".")).replace("id", ""));
				when(f.lastModified()).thenReturn(n * 1000L);
			}
		};
	}

	private static List<String> itemIds(final Collection<ContentItem> items) {
		return items.stream().map(i -> i.getId()).collect(Collectors.toList());
	}

}

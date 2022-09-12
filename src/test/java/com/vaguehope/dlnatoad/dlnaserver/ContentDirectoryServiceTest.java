package com.vaguehope.dlnatoad.dlnaserver;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.ExternalUrls;
import com.vaguehope.dlnatoad.media.MockContent;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ DIDLParser.class, ContentDirectoryService.class })
// https://github.com/mockito/mockito/issues/1562
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*"})
public class ContentDirectoryServiceTest {

	private static final String DIDL_XML = "didl xml";

	private ContentTree contentTree;
	private MockContent mockContent;
	private DIDLParser didlParser;
	private SearchEngine searchEngine;
	private ContentDirectoryService undertest;

	@Before
	public void before () throws Exception {
		this.contentTree = new ContentTree(false);
		this.mockContent = new MockContent(this.contentTree);
		this.mockContent.setShuffle(false);  // FIXME make tests less brittle.
		this.didlParser = mockDidlParser();
		this.searchEngine = mock(SearchEngine.class);
		this.undertest = new ContentDirectoryService(this.contentTree, new NodeConverter(new ExternalUrls("")), this.searchEngine, true);
	}

	/**
	 * ContentDirectory:4, 2.5.7.2:
	 * RequestedCount = 0 indicates request all entries.
	 */
	@Test
	public void itReturnsAllWhenMaxEntitiesZero () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(3);
		final List<ContentItem> items = this.mockContent.givenMockItems(3);
		this.mockContent.addMockItem("item other", dirs.get(1));

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 0, null);

		assertCorrectResult(ret, 6, 6);
		assertParserMarshaled(dirs, items);
	}

	@Test
	public void itReturnsAllItemsWhenTheyAreInsideTheRequetsRange () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(3);
		final List<ContentItem> items = this.mockContent.givenMockItems(3);
		this.mockContent.addMockItem("item other", dirs.get(1));

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 10, null);

		assertCorrectResult(ret, 6, 6);
		assertParserMarshaled(dirs, items);
	}

	@Test
	public void itDoesNotMindIfMaxIsMoreThanCount () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(3);
		final List<ContentItem> items = this.mockContent.givenMockItems(3);
		this.mockContent.addMockItem("item other", dirs.get(1));

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 20, null);

		assertCorrectResult(ret, 6, 6);
		assertParserMarshaled(dirs, items);
	}

	@Test
	public void itReturnsFirstFewDirsWhenOnlyDirsInsideRange () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(5);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 3, null);

		assertCorrectResult(ret, 3, 5);
		assertParserMarshaled(dirs.subList(0, 3), null);
	}

	@Test
	public void itReturnsFirstItemsWhenOnlyItemsInsideRange () throws Exception {
		final List<ContentItem> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 3, null);

		assertCorrectResult(ret, 3, 10);
		assertParserMarshaled(null, items.subList(0, 3));
	}

	@Test
	public void itReturnsDirsThenSomeItemsWhenNotAllItemsFitInsideRange () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(5);
		final List<ContentItem> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 10, null);

		assertCorrectResult(ret, 10, 15);
		assertParserMarshaled(dirs, items.subList(0, 5));
	}

	@Test
	public void itReturnsMidRangeDirsWhenOnlyTheyFallInsideRange () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(10);
		this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 3, 3, null);

		assertCorrectResult(ret, 3, 20);
		assertParserMarshaled(dirs.subList(3, 6), null);
	}

	@Test
	public void itReturnsLastFewDirsThenSomeItems () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(10);
		final List<ContentItem> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 5, 10, null);

		assertCorrectResult(ret, 10, 20);
		assertParserMarshaled(dirs.subList(5, 10), items.subList(0, 5));
	}

	@Test
	public void itReturnsMidRangeItems () throws Exception {
		this.mockContent.givenMockDirs(10);
		final List<ContentItem> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 14, 2, null);

		assertCorrectResult(ret, 2, 20);
		assertParserMarshaled(null, items.subList(4, 6));
	}

	@Test
	public void itReturnsLastFewItems () throws Exception {
		this.mockContent.givenMockDirs(10);
		final List<ContentItem> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 15, 5, null);

		assertCorrectResult(ret, 5, 20);
		assertParserMarshaled(null, items.subList(5, 10));
	}

	@Test
	public void itReturnsItemsFromSubDir () throws Exception {
		final List<ContentNode> rootDirs = this.mockContent.givenMockDirs(5);
		final List<ContentItem> items = this.mockContent.givenMockItems(15, rootDirs.get(0));

		final BrowseResult ret = this.undertest.browse(rootDirs.get(0).getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 10, null);

		assertCorrectResult(ret, 10, 15);
		assertParserMarshaled(null, items.subList(0, 10));
	}

	@Test
	public void itSearchesUsingSearchEngine () throws Exception {
		final List<ContentItem> items = this.mockContent.givenMockItems(10);
		final ContentItem item3 = items.get(3);
		when(this.searchEngine.search(eq(this.contentTree.getRootNode()), ArgumentMatchers.eq("some search query"), anyInt(), eq(null))).thenReturn(Collections.singletonList(item3));

		final BrowseResult ret = this.undertest.search(this.contentTree.getRootNode().getId(), "some search query", "*", 0, 3, null);

		assertCorrectResult(ret, 1, 1);
		assertParserMarshaled(null, items.subList(3, 4));
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private static DIDLParser mockDidlParser () throws Exception {
		final DIDLParser didlParser = mock(DIDLParser.class);
		PowerMockito.whenNew(DIDLParser.class).withNoArguments().thenReturn(didlParser);
		when(didlParser.generate(isA(DIDLContent.class))).thenReturn(DIDL_XML);
		return didlParser;
	}

	private static void assertCorrectResult (final BrowseResult ret, final long count, final long totalMatches) {
		assertEquals(count, ret.getCountLong());
		assertEquals(totalMatches, ret.getTotalMatchesLong());
		assertEquals(DIDL_XML, ret.getResult());
	}

	private void assertParserMarshaled (final List<ContentNode> dirs, final List<ContentItem> items) throws Exception {
		final ArgumentCaptor<DIDLContent> cap = ArgumentCaptor.forClass(DIDLContent.class);
		verify(this.didlParser).generate(cap.capture());
		assertEquals(MockContent.contentIds(dirs), containerIds(cap.getValue().getContainers()));
		assertEquals(MockContent.contentIds(items), itemIds(cap.getValue().getItems()));
	}


	private static List<String> containerIds(final Collection<Container> input) {
		if (input == null) return Collections.emptyList();
		final List<String> ret = new ArrayList<>();
		for (final Container c : input) {
			ret.add(c.getId());
		}
		return ret;
	}

	private static List<String> itemIds(final Collection<Item> input) {
		if (input == null) return Collections.emptyList();
		final List<String> ret = new ArrayList<>();
		for (final Item c : input) {
			ret.add(c.getId());
		}
		return ret;
	}

}

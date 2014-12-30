package com.vaguehope.dlnatoad.dlnaserver;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.teleal.cling.support.contentdirectory.DIDLParser;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.BrowseResult;
import org.teleal.cling.support.model.DIDLContent;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ DIDLParser.class, ContentDirectoryService.class })
public class ContentDirectoryServiceTest {

	private static final String DIDL_XML = "didl xml";

	private ContentTree contentTree;
	private MockContent mockContent;
	private DIDLParser didlParser;
	private SearchEngine searchEngine;
	private ContentDirectoryService undertest;

	@Before
	public void before () throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree);
		this.didlParser = mockDidlParser();
		this.searchEngine = mock(SearchEngine.class);
		this.undertest = new ContentDirectoryService(this.contentTree, this.searchEngine);
	}

	/**
	 * ContentDirectory:4, 2.5.7.2:
	 * RequestedCount = 0 indicates request all entries.
	 */
	@Test
	public void itReturnsAllWhenMaxEntitiesZero () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(3);
		final List<ContentNode> items = this.mockContent.givenMockItems(3);
		this.mockContent.addMockItem("item other", dirs.get(1));

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 0, null);

		assertCorrectResult(ret, 6, 6);
		assertParserMarshaled(dirs, items);
	}

	@Test
	public void itReturnsAllItemsWhenTheyAreInsideTheRequetsRange () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(3);
		final List<ContentNode> items = this.mockContent.givenMockItems(3);
		this.mockContent.addMockItem("item other", dirs.get(1));

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 10, null);

		assertCorrectResult(ret, 6, 6);
		assertParserMarshaled(dirs, items);
	}

	@Test
	public void itDoesNotMindIfMaxIsMoreThanCount () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(3);
		final List<ContentNode> items = this.mockContent.givenMockItems(3);
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
		final List<ContentNode> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 3, null);

		assertCorrectResult(ret, 3, 10);
		assertParserMarshaled(null, items.subList(0, 3));
	}

	@Test
	public void itReturnsDirsThenSomeItemsWhenNotAllItemsFitInsideRange () throws Exception {
		final List<ContentNode> dirs = this.mockContent.givenMockDirs(5);
		final List<ContentNode> items = this.mockContent.givenMockItems(10);

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
		final List<ContentNode> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 5, 10, null);

		assertCorrectResult(ret, 10, 20);
		assertParserMarshaled(dirs.subList(5, 10), items.subList(0, 5));
	}

	@Test
	public void itReturnsMidRangeItems () throws Exception {
		this.mockContent.givenMockDirs(10);
		final List<ContentNode> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 14, 2, null);

		assertCorrectResult(ret, 2, 20);
		assertParserMarshaled(null, items.subList(4, 6));
	}

	@Test
	public void itReturnsLastFewItems () throws Exception {
		this.mockContent.givenMockDirs(10);
		final List<ContentNode> items = this.mockContent.givenMockItems(10);

		final BrowseResult ret = this.undertest.browse(this.contentTree.getRootNode().getId(), BrowseFlag.DIRECT_CHILDREN, null, 15, 5, null);

		assertCorrectResult(ret, 5, 20);
		assertParserMarshaled(null, items.subList(5, 10));
	}

	@Test
	public void itReturnsItemsFromSubDir () throws Exception {
		final List<ContentNode> rootDirs = this.mockContent.givenMockDirs(5);
		final List<ContentNode> items = this.mockContent.givenMockItems(15, rootDirs.get(0));

		final BrowseResult ret = this.undertest.browse(rootDirs.get(0).getId(), BrowseFlag.DIRECT_CHILDREN, null, 0, 10, null);

		assertCorrectResult(ret, 10, 15);
		assertParserMarshaled(null, items.subList(0, 10));
	}

	@Test
	public void itSearchesUsingSearchEngine () throws Exception {
		final List<ContentNode> items = this.mockContent.givenMockItems(10);
		when(this.searchEngine.search(this.contentTree.getRootNode(), "some search query")).thenReturn(Collections.singletonList(items.get(3).getItem()));

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

	private void assertParserMarshaled (final List<ContentNode> dirs, final List<ContentNode> items) throws Exception {
		final ArgumentCaptor<DIDLContent> cap = ArgumentCaptor.forClass(DIDLContent.class);
		verify(this.didlParser).generate(cap.capture());
		assertEquals(MockContent.listOfContainers(dirs), cap.getValue().getContainers());
		assertEquals(MockContent.listOfItems(items), cap.getValue().getItems());
	}

}

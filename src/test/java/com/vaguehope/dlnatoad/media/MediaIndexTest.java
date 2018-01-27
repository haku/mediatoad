package com.vaguehope.dlnatoad.media;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.media.MediaIndex.HierarchyMode;
import com.vaguehope.dlnatoad.util.CollectionHelper;
import com.vaguehope.dlnatoad.util.CollectionHelper.Function;

public class MediaIndexTest {

	private static final String EXTERNAL_HTTP_CONTEXT = "http://foo:123";

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MediaIndex undertest;

	@Before
	public void before () throws Exception {
		this.contentTree = new ContentTree();
		final List<File> roots = new ArrayList<File>();
		roots.add(this.tmp.getRoot());
		this.undertest = new MediaIndex(this.contentTree, EXTERNAL_HTTP_CONTEXT, HierarchyMode.FLATTERN, new MediaId(null), new MediaInfo());
	}

	@Test
	public void itLinksRootToVideos () throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}

		final Container videoContainer = this.contentTree.getRootNode().getContainer().getContainers().get(0);
		final Container dirContainer = videoContainer.getContainers().get(0);

		final List<File> actualFiles = getFiles(getNodes(getItemIds(dirContainer.getItems())));
		assertEquals(expectedFiles, actualFiles);
	}

	@Test
	public void itIndexesAFewVideoRootFiles () throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}

		final List<Container> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getContainer().getContainers();
		assertEquals(1, videoDirs.size());
		final ContentNode node = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedFiles, node);
	}

	@Test
	public void itSeparatesVideosAndImages () throws Exception {
		final List<File> expectedVideos = mockFiles(3, ".mkv");
		final List<File> expectedImages = mockFiles(5, ".jpg");

		for (final File file : expectedVideos) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}
		for (final File file : expectedImages) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}

		final List<Container> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getContainer().getContainers();
		assertEquals(1, videoDirs.size());
		final ContentNode vidDirNode = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedVideos, vidDirNode);

		final List<Container> imageDirs = this.contentTree.getNode(ContentGroup.IMAGE.getId()).getContainer().getContainers();
		assertEquals(1, imageDirs.size());
		final ContentNode imgDirNode = this.contentTree.getNode(imageDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedImages, imgDirNode);
	}

	@Test
	public void itIndexesAFewVideoRootDirs () throws Exception {
		final File dir1 = new File(this.tmp.getRoot(), "dir 1");
		final File dir2 = new File(this.tmp.getRoot(), "dir 2");
		final File file1 = mockFile("file 1.mkv", dir1);
		final File file2 = mockFile("file 2.mkv", dir2);

		this.undertest.fileFound(this.tmp.getRoot(), file1, null);
		this.undertest.fileFound(this.tmp.getRoot(), file2, null);

		final List<Container> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getContainer().getContainers();
		assertEquals(2, videoDirs.size());

		assertContainer(dir1, Arrays.asList(file1), videoDirs.get(0));
		assertContainer(dir2, Arrays.asList(file2), videoDirs.get(1));
	}

	@Test
	public void itCanPreserveDirStructure () throws Exception {
		final File root = new File(this.tmp.getRoot(), "root");
		final File dir1 = new File(root, "dir 1");
		final File dir2 = new File(dir1, "dir 2");
		final File dir3 = new File(dir2, "dir 3");
		final File file1 = mockFile("file 1.mkv", dir1);
		final File file3 = mockFile("file 3.mkv", dir3);

		this.undertest = new MediaIndex(this.contentTree, EXTERNAL_HTTP_CONTEXT, HierarchyMode.PRESERVE, new MediaId(null), new MediaInfo());

		this.undertest.fileFound(root, file1, null);
		this.undertest.fileFound(root, file3, null);

		final List<Container> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getContainer().getContainers();
		assertEquals(1, videoDirs.size());

		final Container rootCont = videoDirs.get(0);
		assertContainerContainsDirsAndFiles(rootCont,
				Collections.singletonList(dir1), Collections.<File> emptyList());
		assertContainerContainsDirsAndFiles(rootCont.getContainers().get(0),
				Collections.singletonList(dir2), Collections.singletonList(file1));
		assertContainerContainsDirsAndFiles(rootCont.getContainers().get(0).getContainers().get(0),
				Collections.singletonList(dir3), Collections.<File> emptyList());
		assertContainerContainsDirsAndFiles(rootCont.getContainers().get(0).getContainers().get(0).getContainers().get(0),
				Collections.<File> emptyList(), Collections.singletonList(file3));
	}

	@Test
	public void itIsCorrectAfterMultipleRefreshes () throws Exception {
		final File rootDir = this.tmp.getRoot();
		final List<File> expectedFiles = mockFiles(3, ".mkv", rootDir);

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}
		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}

		final List<Container> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getContainer().getContainers();
		assertEquals(1, videoDirs.size());
		final ContentNode node = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(rootDir, expectedFiles, node);
	}

	@Test
	public void itDoesNotDuplcateWhenSameFileNameInTwoDirs () throws Exception {
		final String fileName = "file_a.mkv";
		final File file1 = mockFile(fileName);
		final File file2 = mockFile(fileName, new File(this.tmp.getRoot(), "dir"));

		this.undertest.fileFound(this.tmp.getRoot(), file1, null);
		this.undertest.fileFound(this.tmp.getRoot(), file2, null);

		final List<File> actualFiles = new ArrayList<File>();
		for (final ContentNode node : this.contentTree.getNodes()) {
			if (node.isItem() && fileName.equals(node.getItem().getTitle())) actualFiles.add(node.getFile());
		}
		assertThat(actualFiles, hasItems(file1, file2));
		assertEquals(2, actualFiles.size());
	}

	@Test
	public void itDoesNotLeaveAnyDeadEntriesInContentTree () throws Exception {
		final File file1 = mockFile("file_a.mkv");
		final File dir = new File(this.tmp.getRoot(), "dir");
		final File file2 = mockFile("file_b.mkv", dir);

		this.undertest.fileFound(this.tmp.getRoot(), file1, null);
		this.undertest.fileFound(this.tmp.getRoot(), file2, null);
		FileUtils.deleteDirectory(dir);
		this.undertest.fileGone(file2);

		final List<File> actualFiles = getFiles(this.contentTree.getNodes());
		assertThat(actualFiles, hasItem(file1));
		assertThat(actualFiles, not(hasItem(file2)));

		final List<String> nodeTitles = getNodeTitles(getContaners(this.contentTree.getNodes()));
		assertThat(nodeTitles, not(hasItem(dir.getName())));
	}

	@Test
	public void itFindsSubtitlesForVideoFile () throws Exception {
		mockFiles(1, ".foobar"); // Noise.

		final List<File> expectedFiles = mockFiles(3, ".mkv");
		final File videoFile = expectedFiles.get(1);
		final File srtFile = mockFile(videoFile.getName().replaceFirst("\\.mkv$", ".srt"), videoFile.getParentFile());

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}

		final List<Container> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getContainer().getContainers();
		final Item item = this.contentTree.getNode(videoDirs.get(0).getId()).getContainer().getItems().get(1);
		assertEquals(videoFile, this.contentTree.getNode(item.getResources().get(0).getValue().replace(EXTERNAL_HTTP_CONTEXT + "/", "")).getFile());
		assertEquals(srtFile, this.contentTree.getNode(item.getResources().get(1).getValue().replace(EXTERNAL_HTTP_CONTEXT + "/", "")).getFile());
	}

	@Test
	public void itAttachesOrDetachesSubtitlesWhenTheyAppearLaterOrDisappear () throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}

		final File videoFile = expectedFiles.get(1);
		final File srtFile = mockFile(videoFile.getName().replaceFirst("\\.mkv$", ".srt"), videoFile.getParentFile());
		this.undertest.fileFound(this.tmp.getRoot(), srtFile, null);

		final List<Container> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getContainer().getContainers();
		final Item item = this.contentTree.getNode(videoDirs.get(0).getId()).getContainer().getItems().get(1);
		assertEquals(videoFile, this.contentTree.getNode(item.getResources().get(0).getValue().replace(EXTERNAL_HTTP_CONTEXT + "/", "")).getFile());
		assertEquals(srtFile, this.contentTree.getNode(item.getResources().get(1).getValue().replace(EXTERNAL_HTTP_CONTEXT + "/", "")).getFile());

		this.undertest.fileGone(srtFile);
		assertEquals(videoFile, this.contentTree.getNode(item.getResources().get(0).getValue().replace(EXTERNAL_HTTP_CONTEXT + "/", "")).getFile());
		assertEquals(1, item.getResources().size());
	}

	@Test
	public void itAddsItemThumbnail () throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		final File videoFile = expectedFiles.get(1);
		final File artFile = mockFile(videoFile.getName().replaceFirst("\\.mkv$", ".jpg"), videoFile.getParentFile());

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null);
		}

		final List<Container> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getContainer().getContainers();
		final Item item = this.contentTree.getNode(videoDirs.get(0).getId()).getContainer().getItems().get(1);
		assertEquals(videoFile, this.contentTree.getNode(item.getResources().get(0).getValue().replace(EXTERNAL_HTTP_CONTEXT + "/", "")).getFile());
		assertEquals(artFile, this.contentTree.getNode(item.getResources().get(1).getValue().replace(EXTERNAL_HTTP_CONTEXT + "/", "")).getFile());
		assertEquals(
				"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN;DLNA.ORG_OP=01;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=00d00000000000000000000000000000",
				item.getResources().get(1).getProtocolInfo().toString());
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private List<File> mockFiles (final int n, final String ext) throws IOException {
		return mockFiles(n, ext, this.tmp.getRoot());
	}

	private static List<File> mockFiles (final int n, final String ext, final File parent) throws IOException {
		final List<File> ret = new ArrayList<File>();
		for (int i = 0; i < n; i++) {
			final File f = mockFile("file_" + i + ext, parent);
			ret.add(f);
		}
		return ret;
	}

	private File mockFile (final String name) throws IOException {
		return mockFile(name, this.tmp.getRoot());
	}

	private static File mockFile (final String name, final File parent) throws IOException {
		final File f = new File(parent, name);
		FileUtils.touch(f);
		return f;
	}

	public static List<String> getItemIds (final List<Item> items) {
		final List<String> ids = new ArrayList<String>();
		for (final Item item : items) {
			ids.add(item.getId());
		}
		return ids;
	}

	public static List<String> getNodeIds (final List<Container> list) {
		final List<String> ids = new ArrayList<String>();
		for (final Container i : list) {
			ids.add(i.getId());
		}
		return ids;
	}

	public static List<String> getNodeTitles (final List<Container> list) {
		final List<String> ret = new ArrayList<String>();
		for (final Container i : list) {
			ret.add(i.getTitle());
		}
		return ret;
	}

	private List<ContentNode> getNodes (final List<String> ids) {
		final List<ContentNode> nodes = new ArrayList<ContentNode>();
		for (final String id : ids) {
			nodes.add(this.contentTree.getNode(id));
		}
		return nodes;
	}

	private static List<File> getFiles (final Collection<ContentNode> nodes) {
		final List<File> files = new ArrayList<File>();
		for (final ContentNode node : nodes) {
			if (node.isItem()) files.add(node.getFile());
		}
		return files;
	}

	private static List<Container> getContaners (final Collection<ContentNode> nodes) {
		final List<Container> cs = new ArrayList<Container>();
		for (final ContentNode node : nodes) {
			if (!node.isItem()) cs.add(node.getContainer());
		}
		return cs;
	}

	private void assertContainer (final File dir, final List<File> files, final Container cont) {
		assertEquals(dir.getName(), cont.getTitle());
		final ContentNode node = this.contentTree.getNode(cont.getId());
		assertNodeWithItems(dir, files, node);
	}

	private void assertNodeWithItems (final File rootDir, final List<File> expectedFiles, final ContentNode node) {
		assertFalse(node.isItem());
		final Container container = node.getContainer();
		assertEquals(rootDir.getName(), container.getTitle());
		assertEquals(expectedFiles.size(), container.getChildCount().intValue());

		final List<File> actualFiles = getFiles(getNodes(getItemIds(container.getItems())));
		assertEquals(expectedFiles, actualFiles);
	}

	private static void assertContainerContainsDirsAndFiles (final Container cont, final List<File> dirs, final List<File> files) {
		final Collection<String> expectedDirs = CollectionHelper.map(dirs, new Function<File, String>() {
			@Override
			public String exec (final File input) {
				return input.getName();
			}
		}, new ArrayList<String>());
		final Collection<String> actualDirs = CollectionHelper.map(cont.getContainers(), new Function<Container, String>() {
			@Override
			public String exec (final Container input) {
				return input.getTitle();
			}
		}, new ArrayList<String>());
		assertEquals(expectedDirs, actualDirs);

		final Collection<String> expectedFiles = CollectionHelper.map(files, new Function<File, String>() {
			@Override
			public String exec (final File input) {
				return input.getName();
			}
		}, new ArrayList<String>());
		final Collection<String> actualFiles = CollectionHelper.map(cont.getItems(), new Function<Item, String>() {
			@Override
			public String exec (final Item input) {
				return input.getTitle();
			}
		}, new ArrayList<String>());
		assertEquals(expectedFiles, actualFiles);
	}

}

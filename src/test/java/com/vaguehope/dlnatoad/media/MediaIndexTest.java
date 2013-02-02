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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;

public class MediaIndexTest {

	private static final String EXTERNAL_HTTP_CONTEXT = "http://foo:123";

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MediaIndex undertest;

	@Before
	public void before () throws Exception {
		this.contentTree = new ContentTree();
		List<File> roots = new ArrayList<File>();
		roots.add(this.tmp.getRoot());
		this.undertest = new MediaIndex(roots, this.contentTree, EXTERNAL_HTTP_CONTEXT);
	}

	@Test
	public void itLinksRootToVideos () throws Exception {
		List<File> expectedFiles = mockFiles(3, ".mkv");

		this.undertest.refresh();

		Container videoContainer = this.contentTree.getRootNode().getContainer().getContainers().get(0);
		Container dirContainer = videoContainer.getContainers().get(0);

		List<File> actualFiles = getFiles(getNodes(getItemIds(dirContainer.getItems())));
		assertEquals(expectedFiles, actualFiles);
	}

	@Test
	public void itIndexesAFewVideoRootFiles () throws Exception {
		List<File> expectedFiles = mockFiles(3, ".mkv");

		this.undertest.refresh();

		List<Container> videoDirs = this.contentTree.getNode(ContentTree.VIDEO_ID).getContainer().getContainers();
		assertEquals(1, videoDirs.size());
		ContentNode node = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedFiles, node);
	}

	@Test
	public void itSeparatesVideosAndImages () throws Exception {
		List<File> expectedVideos = mockFiles(3, ".mkv");
		List<File> expectedImages = mockFiles(5, ".jpg");

		this.undertest.refresh();

		List<Container> videoDirs = this.contentTree.getNode(ContentTree.VIDEO_ID).getContainer().getContainers();
		assertEquals(1, videoDirs.size());
		ContentNode vidDirNode = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedVideos, vidDirNode);

		List<Container> imageDirs = this.contentTree.getNode(ContentTree.IMAGE_ID).getContainer().getContainers();
		assertEquals(1, imageDirs.size());
		ContentNode imgDirNode = this.contentTree.getNode(imageDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedImages, imgDirNode);
	}

	@Test
	public void itIndexesAFewVideoRootDirs () throws Exception {
		File dir1 = new File(this.tmp.getRoot(), "dir 1");
		File dir2 = new File(this.tmp.getRoot(), "dir 2");
		File file1 = mockFile("file 1.mkv", dir1);
		File file2 = mockFile("file 2.mkv", dir2);

		this.undertest.refresh();

		List<Container> videoDirs = this.contentTree.getNode(ContentTree.VIDEO_ID).getContainer().getContainers();
		assertEquals(2, videoDirs.size());

		assertContainer(dir1, Arrays.asList(file1), videoDirs.get(0));
		assertContainer(dir2, Arrays.asList(file2), videoDirs.get(1));
	}

	@Test
	public void itIsCorrectAfterMultipleRefreshes () throws Exception {
		File rootDir = this.tmp.getRoot();
		List<File> expectedFiles = mockFiles(3, ".mkv", rootDir);

		this.undertest.refresh();
		this.undertest.refresh();

		List<Container> videoDirs = this.contentTree.getNode(ContentTree.VIDEO_ID).getContainer().getContainers();
		assertEquals(1, videoDirs.size());
		ContentNode node = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(rootDir, expectedFiles, node);
	}

	@Test
	public void itDoesNotDuplcateWhenSameFileNameInTwoDirs () throws Exception {
		String fileName = "file_a.mkv";
		File file1 = mockFile(fileName);
		File file2 = mockFile(fileName, new File(this.tmp.getRoot(), "dir"));

		this.undertest.refresh();

		List<File> actualFiles = new ArrayList<File>();
		for (ContentNode node : this.contentTree.getNodes()) {
			if (node.isItem() && fileName.equals(node.getItem().getTitle())) actualFiles.add(node.getFile());
		}
		assertThat(actualFiles, hasItems(file1, file2));
		assertEquals(2, actualFiles.size());
	}

	@Test
	public void itDoesNotLeaveAnyDeadEntriesInContentTree () throws Exception {
		File file1 = mockFile("file_a.mkv");
		File dir = new File(this.tmp.getRoot(), "dir");
		File file2 = mockFile("file_b.mkv", dir);

		this.undertest.refresh();
		FileUtils.deleteDirectory(dir);
		this.undertest.refresh();

		List<File> actualFiles = getFiles(this.contentTree.getNodes());
		assertThat(actualFiles, hasItem(file1));
		assertThat(actualFiles, not(hasItem(file2)));

		List<String> nodeTitles = getNodeTitles(getContaners(this.contentTree.getNodes()));
		assertThat(nodeTitles, not(hasItem(dir.getName())));
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private List<File> mockFiles (int n, String ext) throws IOException {
		return mockFiles(n, ext, this.tmp.getRoot());
	}

	private static List<File> mockFiles (int n, String ext, File parent) throws IOException {
		List<File> ret = new ArrayList<File>();
		for (int i = 0; i < n; i++) {
			File f = mockFile("file_" + i + ext, parent);
			ret.add(f);
		}
		return ret;
	}

	private File mockFile (String name) throws IOException {
		return mockFile(name, this.tmp.getRoot());
	}

	private static File mockFile (String name, File parent) throws IOException {
		File f = new File(parent, name);
		FileUtils.touch(f);
		return f;
	}

	public static List<String> getItemIds (List<Item> items) {
		List<String> ids = new ArrayList<String>();
		for (Item item : items) {
			ids.add(item.getId());
		}
		return ids;
	}

	public static List<String> getNodeIds (List<Container> list) {
		List<String> ids = new ArrayList<String>();
		for (Container i : list) {
			ids.add(i.getId());
		}
		return ids;
	}

	public static List<String> getNodeTitles (List<Container> list) {
		List<String> ret = new ArrayList<String>();
		for (Container i : list) {
			ret.add(i.getTitle());
		}
		return ret;
	}

	private List<ContentNode> getNodes (List<String> ids) {
		List<ContentNode> nodes = new ArrayList<ContentNode>();
		for (String id : ids) {
			nodes.add(this.contentTree.getNode(id));
		}
		return nodes;
	}

	private static List<File> getFiles (Collection<ContentNode> nodes) {
		List<File> files = new ArrayList<File>();
		for (ContentNode node : nodes) {
			if (node.isItem()) files.add(node.getFile());
		}
		return files;
	}

	private static List<Container> getContaners (Collection<ContentNode> nodes) {
		List<Container> cs = new ArrayList<Container>();
		for (ContentNode node : nodes) {
			if (!node.isItem()) cs.add(node.getContainer());
		}
		return cs;
	}

	private void assertContainer (File dir, List<File> files, Container cont1) {
		assertEquals(dir.getName(), cont1.getTitle());
		ContentNode node1 = this.contentTree.getNode(cont1.getId());
		assertNodeWithItems(dir, files, node1);
	}

	private void assertNodeWithItems (File rootDir, List<File> expectedFiles, ContentNode node) {
		assertFalse(node.isItem());
		Container container = node.getContainer();
		assertEquals(rootDir.getName(), container.getTitle());
		assertEquals(expectedFiles.size(), container.getChildCount().intValue());

		List<File> actualFiles = getFiles(getNodes(getItemIds(container.getItems())));
		assertEquals(expectedFiles, actualFiles);
	}

}

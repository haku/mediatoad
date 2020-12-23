package com.vaguehope.dlnatoad.media;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.dlnaserver.ContentGroup;
import com.vaguehope.dlnatoad.dlnaserver.ContentNode;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.dlnaserver.MockContent;
import com.vaguehope.dlnatoad.media.MediaIndex.HierarchyMode;
import com.vaguehope.dlnatoad.util.CollectionHelper;
import com.vaguehope.dlnatoad.util.CollectionHelper.Function;
import com.vaguehope.dlnatoad.util.DaemonThreadFactory;

public class MediaIndexTest {

	private static final String EXTERNAL_HTTP_CONTEXT = "http://foo:123";

	private static final long TEST_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MediaDb mediaDb;
	private ScheduledThreadPoolExecutor schEx;
	private MediaIndex undertest;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.schEx = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("fs"));
		final List<File> roots = new ArrayList<>();
		roots.add(this.tmp.getRoot());
		this.mediaDb = new MediaDb("file:testdb?mode=memory&cache=shared", this.schEx);
		this.undertest = new MediaIndex(this.contentTree, EXTERNAL_HTTP_CONTEXT, HierarchyMode.FLATTERN,
				new MediaId(this.mediaDb), new MediaInfo());
	}

	public void after() {
		if (this.schEx != null) this.schEx.shutdownNow();
	}

	@Test
	public void itLinksRootToVideos() throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();

		final List<Container> toplevelContainers = MockContent.childContainers(this.contentTree.getRootNode());
		Container videoContainer = null;
		for (final Container tlc : toplevelContainers) {
			if (ContentGroup.VIDEO.getId().equals(tlc.getId())) {
				videoContainer = tlc;
				break;
			}
		}
		if (videoContainer == null) fail("Video container not found.");

		final Container dirContainer = videoContainer.getContainers().get(0);
		final List<File> actualFiles = getFiles(getNodes(getItemIds(dirContainer.getItems())));
		assertEquals(expectedFiles, actualFiles);
	}

	@Test
	public void itIndexesAFewVideoRootFiles() throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();

		final List<Container> videoDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.VIDEO.getId()));
		assertEquals(1, videoDirs.size());
		final ContentNode node = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedFiles, node);
	}

	@Test
	public void itSeparatesVideosAndImages() throws Exception {
		final List<File> expectedVideos = mockFiles(3, ".mkv");
		final List<File> expectedImages = mockFiles(5, ".jpg");

		for (final File file : expectedVideos) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		for (final File file : expectedImages) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();

		final List<Container> videoDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.VIDEO.getId()));
		assertEquals(1, videoDirs.size());
		final ContentNode vidDirNode = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedVideos, vidDirNode);

		final List<Container> imageDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.IMAGE.getId()));
		assertEquals(1, imageDirs.size());
		final ContentNode imgDirNode = this.contentTree.getNode(imageDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedImages, imgDirNode);
	}

	@Test
	public void itIndexesAFewVideoRootDirs() throws Exception {
		final File dir1 = new File(this.tmp.getRoot(), "dir 1");
		final File dir2 = new File(this.tmp.getRoot(), "dir 2");
		final File file1 = mockFile("file 1.mkv", dir1);
		final File file2 = mockFile("file 2.mkv", dir2);

		this.undertest.fileFound(this.tmp.getRoot(), file1, null, null);
		this.undertest.fileFound(this.tmp.getRoot(), file2, null, null);
		waitForEmptyQueue();

		final List<Container> videoDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.VIDEO.getId()));
		assertEquals(2, videoDirs.size());

		assertContainer(dir1, Arrays.asList(file1), videoDirs.get(0));
		assertContainer(dir2, Arrays.asList(file2), videoDirs.get(1));
	}

	@Test
	public void itCanPreserveDirStructure() throws Exception {
		final File topdir = new File(this.tmp.getRoot(), "topdir");
		final File dir1 = new File(topdir, "dir 1");
		final File dir2 = new File(dir1, "dir 2");
		final File dir3 = new File(dir2, "dir 3");
		final File file1 = mockFile("file 1.mkv", dir1);
		final File file3 = mockFile("file 3.mkv", dir3);

		this.contentTree = new ContentTree(); // Reset it.
		this.undertest = new MediaIndex(this.contentTree, EXTERNAL_HTTP_CONTEXT, HierarchyMode.PRESERVE,
				new MediaId(this.mediaDb), new MediaInfo());

		this.undertest.fileFound(topdir, file1, null, null);
		this.undertest.fileFound(topdir, file3, null, null);
		waitForEmptyQueue();

		final List<Container> rootDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.ROOT.getId()));

		final Container rootCont = rootDirs.get(rootDirs.size() - 1);
		assertEquals("topdir", rootCont.getTitle());
		assertContainerContainsDirsAndFiles(rootCont,
				Collections.singletonList(dir1), Collections.<File>emptyList());
		assertContainerContainsDirsAndFiles(rootCont.getContainers().get(0),
				Collections.singletonList(dir2), Collections.singletonList(file1));
		assertContainerContainsDirsAndFiles(rootCont.getContainers().get(0).getContainers().get(0),
				Collections.singletonList(dir3), Collections.<File>emptyList());
		assertContainerContainsDirsAndFiles(
				rootCont.getContainers().get(0).getContainers().get(0).getContainers().get(0),
				Collections.<File>emptyList(), Collections.singletonList(file3));
	}

	@Test
	public void itIsCorrectAfterMultipleRefreshes() throws Exception {
		final File rootDir = this.tmp.getRoot();
		final List<File> expectedFiles = mockFiles(3, ".mkv", rootDir);

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();

		final List<Container> videoDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.VIDEO.getId()));
		assertEquals(1, videoDirs.size());
		final ContentNode node = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(rootDir, expectedFiles, node);
	}

	@Test
	public void itDoesNotDuplcateWhenSameFileNameInTwoDirs() throws Exception {
		final String fileName = "file_a.mkv";
		final File file1 = mockFile(fileName);
		final File file2 = mockFile(fileName, new File(this.tmp.getRoot(), "dir"));

		this.undertest.fileFound(this.tmp.getRoot(), file1, null, null);
		this.undertest.fileFound(this.tmp.getRoot(), file2, null, null);
		waitForEmptyQueue();

		final List<File> actualFiles = new ArrayList<>();
		for (final ContentNode node : this.contentTree.getNodes()) {
			if (node.isItem() && fileName.equals(node.getTitle())) actualFiles.add(node.getFile());
		}
		assertThat(actualFiles, hasItems(file1, file2));
		assertEquals(2, actualFiles.size());
	}

	@Test
	public void itDoesNotLeaveAnyDeadEntriesInContentTree() throws Exception {
		final File file1 = mockFile("file_a.mkv");
		final File dir = new File(this.tmp.getRoot(), "dir");
		final File file2 = mockFile("file_b.mkv", dir);

		this.undertest.fileFound(this.tmp.getRoot(), file1, null, null);
		this.undertest.fileFound(this.tmp.getRoot(), file2, null, null);
		waitForEmptyQueue();

		FileUtils.deleteDirectory(dir);
		this.undertest.fileGone(file2);
		waitForEmptyQueue();

		final List<File> actualFiles = getFiles(this.contentTree.getNodes());
		assertThat(actualFiles, hasItem(file1));
		assertThat(actualFiles, not(hasItem(file2)));

		final List<String> nodeTitles = getNodeTitles(getContaners(this.contentTree.getNodes()));
		assertThat(nodeTitles, not(hasItem(dir.getName())));
	}

	@Test
	public void itFindsSubtitlesForVideoFile() throws Exception {
		mockFiles(1, ".foobar"); // Noise.

		final List<File> expectedFiles = mockFiles(3, ".mkv");
		final File videoFile = expectedFiles.get(1);
		final File srtFile = mockFile(videoFile.getName().replaceFirst("\\.mkv$", ".srt"), videoFile.getParentFile());

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();

		final List<Container> videoDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.VIDEO.getId()));
		final Item item = this.contentTree.getNode(videoDirs.get(0).getId()).applyContainer(c -> c.getItems().get(1));
		assertEquals(videoFile, this.contentTree.getNode(pathWithoutPrefix(item.getResources().get(0))).getFile());
		assertEquals(srtFile, this.contentTree.getNode(pathWithoutPrefix(item.getResources().get(1))).getFile());
	}

	@Test
	public void itAttachesOrDetachesSubtitlesWhenTheyAppearLaterOrDisappear() throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}

		final File videoFile = expectedFiles.get(1);
		final File srtFile = mockFile(videoFile.getName().replaceFirst("\\.mkv$", ".srt"), videoFile.getParentFile());
		this.undertest.fileFound(this.tmp.getRoot(), srtFile, null, null);
		waitForEmptyQueue();

		final List<Container> videoDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.VIDEO.getId()));
		final Item item = this.contentTree.getNode(videoDirs.get(0).getId()).applyContainer(c -> c.getItems().get(1));
		assertEquals(videoFile, this.contentTree.getNode(pathWithoutPrefix(item.getResources().get(0))).getFile());
		assertEquals(srtFile, this.contentTree.getNode(pathWithoutPrefix(item.getResources().get(1))).getFile());

		this.undertest.fileGone(srtFile);
		assertEquals(videoFile, this.contentTree.getNode(pathWithoutPrefix(item.getResources().get(0))).getFile());
		assertEquals(1, item.getResources().size());
	}

	@Test
	public void itAddsItemThumbnail() throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		final File videoFile = expectedFiles.get(1);
		final File artFile = mockFile(videoFile.getName().replaceFirst("\\.mkv$", ".jpg"), videoFile.getParentFile());

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();

		final List<Container> videoDirs = MockContent
				.childContainers(this.contentTree.getNode(ContentGroup.VIDEO.getId()));
		final Item item = this.contentTree.getNode(videoDirs.get(0).getId()).applyContainer(c -> c.getItems().get(1));
		assertEquals(videoFile, this.contentTree.getNode(pathWithoutPrefix(item.getResources().get(0))).getFile());
		assertEquals(artFile, this.contentTree.getNode(pathWithoutPrefix(item.getResources().get(1))).getFile());
		assertEquals(
				"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN;DLNA.ORG_OP=01;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=00d00000000000000000000000000000",
				item.getResources().get(1).getProtocolInfo().toString());
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private void waitForEmptyQueue() throws InterruptedException, SecurityException, ReflectiveOperationException {
		final long start = System.nanoTime();
		List<Runnable> tasks = null;
		while (System.nanoTime() - start < TEST_TIMEOUT_NANOS) {
			tasks = new ArrayList<>();
			for (final Runnable r : this.schEx.getQueue()) {
				if (r instanceof RunnableScheduledFuture && ((RunnableScheduledFuture<?>) r).isPeriodic()) continue;
				tasks.add(r);
			}
			if (tasks.size() == 0) return;
			Thread.sleep(200);
		}
		fail("After timeout queue has items: " + tasks);
	}

	private List<File> mockFiles(final int n, final String ext) throws IOException {
		return mockFiles(n, ext, this.tmp.getRoot());
	}

	private static List<File> mockFiles(final int n, final String ext, final File parent) throws IOException {
		final List<File> ret = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			final File f = mockFile("file_" + i + ext, parent);
			ret.add(f);
		}
		return ret;
	}

	private File mockFile(final String name) throws IOException {
		return mockFile(name, this.tmp.getRoot());
	}

	private static File mockFile(final String name, final File parent) throws IOException {
		final File f = new File(parent, name);
		FileUtils.write(f, f.getAbsolutePath(), Charset.defaultCharset()); // Make each file unique.
		return f;
	}

	private static List<String> getItemIds(final Collection<Item> items) {
		final List<String> ids = new ArrayList<>();
		for (final Item item : items) {
			ids.add(item.getId());
		}
		return ids;
	}

	private static List<String> getNodeTitles(final Collection<Container> list) {
		final List<String> ret = new ArrayList<>();
		for (final Container i : list) {
			ret.add(i.getTitle());
		}
		return ret;
	}

	private List<ContentNode> getNodes(final Collection<String> ids) {
		final List<ContentNode> nodes = new ArrayList<>();
		for (final String id : ids) {
			nodes.add(this.contentTree.getNode(id));
		}
		return nodes;
	}

	private static List<File> getFiles(final Collection<ContentNode> nodes) {
		final List<File> files = new ArrayList<>();
		for (final ContentNode node : nodes) {
			if (node.isItem()) files.add(node.getFile());
		}
		return files;
	}

	private static List<Container> getContaners(final Collection<ContentNode> nodes) {
		final List<Container> cs = new ArrayList<>();
		for (final ContentNode node : nodes) {
			if (node.hasContainer()) {
				node.withContainer(c -> cs.add(c));
			}
		}
		return cs;
	}

	private void assertContainer(final File dir, final List<File> files, final Container cont) {
		assertEquals(dir.getName(), cont.getTitle());
		final ContentNode node = this.contentTree.getNode(cont.getId());
		assertNodeWithItems(dir, files, node);
	}

	private void assertNodeWithItems(final File rootDir, final List<File> expectedFiles, final ContentNode node) {
		assertFalse(node.isItem());
		assertEquals(rootDir.getName(), node.getTitle());

		final List<File> actualFiles = node.applyContainer(c -> {
			return getFiles(getNodes(getItemIds(c.getItems())));
		});
		assertEquals(expectedFiles, actualFiles);
	}

	private static void assertContainerContainsDirsAndFiles(final Container cont, final List<File> dirs,
			final List<File> files) {
		final Collection<String> expectedDirs = CollectionHelper.map(dirs, new Function<File, String>() {
			@Override
			public String exec(final File input) {
				return input.getName();
			}
		}, new ArrayList<String>());
		final Collection<String> actualDirs = CollectionHelper.map(cont.getContainers(),
				new Function<Container, String>() {
					@Override
					public String exec(final Container input) {
						return input.getTitle();
					}
				}, new ArrayList<String>());
		assertEquals(expectedDirs, actualDirs);

		final Collection<String> expectedFiles = CollectionHelper.map(files, new Function<File, String>() {
			@Override
			public String exec(final File input) {
				return input.getName();
			}
		}, new ArrayList<String>());
		final Collection<String> actualFiles = CollectionHelper.map(cont.getItems(), new Function<Item, String>() {
			@Override
			public String exec(final Item input) {
				return input.getTitle();
			}
		}, new ArrayList<String>());
		assertEquals(expectedFiles, actualFiles);
	}

	private static String pathWithoutPrefix(final Res res) {
		return res.getValue().replace(EXTERNAL_HTTP_CONTEXT + "/" + C.CONTENT_PATH_PREFIX, "");
	}

}

package com.vaguehope.dlnatoad.media;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.db.InMemoryMediaDb;
import com.vaguehope.dlnatoad.db.MediaMetadataStore;
import com.vaguehope.dlnatoad.media.MediaIndex.HierarchyMode;
import com.vaguehope.dlnatoad.util.CollectionHelper;
import com.vaguehope.dlnatoad.util.CollectionHelper.Function;
import com.vaguehope.dlnatoad.util.DaemonThreadFactory;

public class MediaIndexTest {

	private static final long TEST_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MediaMetadataStore mediaMetadataStore;
	private MediaId mediaId;
	private ScheduledThreadPoolExecutor schEx;
	private MediaIndex undertest;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.schEx = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("fs"));
		final List<File> roots = new ArrayList<>();
		roots.add(this.tmp.getRoot());
		this.mediaMetadataStore = new MediaMetadataStore(new InMemoryMediaDb(), this.schEx, true);
		this.mediaId = spy(new MediaId(this.mediaMetadataStore));
		this.undertest = new MediaIndex(this.contentTree, HierarchyMode.FLATTERN, this.mediaId, new MediaInfo());
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

		ContentNode videoNode = null;
		for (final ContentNode tln : this.contentTree.getRootNode().getCopyOfNodes()) {
			if (ContentGroup.VIDEO.getId().equals(tln.getId())) {
				videoNode = tln;
				break;
			}
		}
		if (videoNode == null) fail("Video node not found.");

		final ContentNode dirContainer = videoNode.getCopyOfNodes().iterator().next();
		final List<File> actualFiles = getFiles(dirContainer.getCopyOfItems());
		assertEquals(expectedFiles, actualFiles);
	}

	@Test
	public void itIndexesAFewVideoRootFiles() throws Exception {
		final List<File> expectedFiles = mockFiles(3, ".mkv");

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();

		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
		assertEquals(1, videoDirs.size());
		final ContentNode node = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedFiles, node);
	}

	@Test
	public void itHandlesModifiedFile() throws Exception {
		final File file = mockFile("foo.mkv");
		this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		waitForEmptyQueue();

		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
		assertEquals(1, videoDirs.size());
		final ContentNode node = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), Arrays.asList(file), node);

		FileUtils.touch(file);
		this.undertest.fileModified(this.tmp.getRoot(), file, null);
		waitForEmptyQueue();

		assertNodeWithItems(this.tmp.getRoot(), Arrays.asList(file), node);
	}

	@Test
	public void itDoesNotErrorOnModifiedSubtitles() throws Exception {
		// This is just to check for exceptions from fileModififed() handling a ContentGroup.SUBTITLES.
		final File file = mockFile("foo.srt");
		this.undertest.fileModified(this.tmp.getRoot(), file, null);
		waitForEmptyQueue();
	}

	@Test
	public void itHandlesDeletedFile() throws Exception {
		final File file = mockFile("foo.mkv");
		this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		waitForEmptyQueue();

		this.undertest.fileGone(file);
		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
		assertEquals(0, videoDirs.size());
		verify(this.mediaId).fileGoneAsync(file);
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

		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
		assertEquals(1, videoDirs.size());
		final ContentNode vidDirNode = this.contentTree.getNode(videoDirs.get(0).getId());
		assertNodeWithItems(this.tmp.getRoot(), expectedVideos, vidDirNode);

		final List<ContentNode> imageDirs = this.contentTree.getNode(ContentGroup.IMAGE.getId()).getCopyOfNodes();
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

		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
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
		this.undertest = new MediaIndex(this.contentTree, HierarchyMode.PRESERVE,
				new MediaId(this.mediaMetadataStore), new MediaInfo());

		this.undertest.fileFound(topdir, file1, null, null);
		this.undertest.fileFound(topdir, file3, null, null);
		waitForEmptyQueue();

		final List<ContentNode> rootDirs = this.contentTree.getNode(ContentGroup.ROOT.getId()).getCopyOfNodes();

		final ContentNode rootCont = rootDirs.get(rootDirs.size() - 1);
		assertEquals("topdir", rootCont.getTitle());
		assertContainerContainsDirsAndFiles(rootCont,
				Collections.singletonList(dir1), Collections.<File>emptyList());
		assertContainerContainsDirsAndFiles(rootCont.getCopyOfNodes().get(0),
				Collections.singletonList(dir2), Collections.singletonList(file1));
		assertContainerContainsDirsAndFiles(rootCont.getCopyOfNodes().get(0).getCopyOfNodes().get(0),
				Collections.singletonList(dir3), Collections.<File>emptyList());
		assertContainerContainsDirsAndFiles(
				rootCont.getCopyOfNodes().get(0).getCopyOfNodes().get(0).getCopyOfNodes().get(0),
				Collections.<File>emptyList(), Collections.singletonList(file3));
	}

	@Test
	public void itIsCorrectAfterMultipleRefreshes() throws Exception {
		final File rootDir = this.tmp.getRoot();
		final List<File> expectedFiles = mockFiles(3, ".mkv", rootDir);

		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();
		for (final File file : expectedFiles) {
			this.undertest.fileFound(this.tmp.getRoot(), file, null, null);
		}
		waitForEmptyQueue();

		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
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
		for (final ContentItem item : this.contentTree.getItems()) {
			if (fileName.equals(item.getTitle())) actualFiles.add(item.getFile());
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

		final List<File> actualFiles = getFiles(this.contentTree.getItems());
		assertThat(actualFiles, hasItem(file1));
		assertThat(actualFiles, not(hasItem(file2)));

		final List<String> nodeTitles = getNodeTitles(this.contentTree.getNodes());
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

		// Check things are in the tree.
		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
		final ContentNode videoDir = this.contentTree.getNode(videoDirs.get(0).getId());
		final ContentItem item = this.contentTree.getItem(videoDir.getCopyOfItems().get(1).getId());
		final ContentItem attachment = this.contentTree.getItem(item.getCopyOfAttachments().get(0).getId());
		assertEquals(videoFile, item.getFile());
		assertEquals(srtFile, attachment.getFile());
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

		// Check things are in the tree.
		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
		final ContentNode videoDir = this.contentTree.getNode(videoDirs.get(0).getId());
		final ContentItem item = this.contentTree.getItem(videoDir.getCopyOfItems().get(1).getId());
		assertEquals(videoFile, item.getFile());
		final ContentItem attachment = this.contentTree.getItem(item.getCopyOfAttachments().get(0).getId());
		assertEquals(srtFile, attachment.getFile());

		this.undertest.fileGone(srtFile);
		waitForEmptyQueue();

		final ContentItem itemStillInTree = this.contentTree.getItem(item.getId());
		assertEquals(videoFile, itemStillInTree.getFile());
		assertEquals(0, itemStillInTree.getCopyOfAttachments().size());
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

		// Check things are in the tree.
		final List<ContentNode> videoDirs = this.contentTree.getNode(ContentGroup.VIDEO.getId()).getCopyOfNodes();
		final ContentNode videoDir = this.contentTree.getNode(videoDirs.get(0).getId());
		final ContentItem item = this.contentTree.getItem(videoDir.getCopyOfItems().get(1).getId());
		assertEquals(videoFile, item.getFile());
		final ContentItem art = this.contentTree.getItem(item.getArt().getId());
		assertEquals(artFile, art.getFile());
	}

	@Test
	public void itDoesNotAddArtForImages() throws Exception {
		final File expectedFile = mockFile("image.jpeg");
		this.undertest.fileFound(this.tmp.getRoot(), expectedFile, null, null);
		waitForEmptyQueue();

		final List<ContentNode> dirs = this.contentTree.getNode(ContentGroup.IMAGE.getId()).getCopyOfNodes();
		final ContentNode dir = this.contentTree.getNode(dirs.get(0).getId());
		final ContentItem item = this.contentTree.getItem(dir.getCopyOfItems().get(0).getId());

		// If a thumbnail was found it would overwrite the item with a null title.
		assertEquals("image.jpeg", item.getTitle());
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
			if (tasks.size() == 0 && this.schEx.getActiveCount() < 1) return;
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

	private static List<String> getNodeTitles(final Collection<ContentNode> nodes) {
		final List<String> ret = new ArrayList<>();
		for (final ContentNode i : nodes) {
			ret.add(i.getTitle());
		}
		return ret;
	}

	private static List<File> getFiles(final Collection<ContentItem> items) {
		final List<File> files = new ArrayList<>();
		for (final ContentItem node : items) {
			files.add(node.getFile());
		}
		return files;
	}

	private void assertContainer(final File dir, final List<File> files, final ContentNode node) {
		assertEquals(dir.getName(), node.getTitle());
		final ContentNode nodeOnTree = this.contentTree.getNode(node.getId());
		assertNodeWithItems(dir, files, nodeOnTree);
	}

	private static void assertNodeWithItems(final File rootDir, final List<File> expectedFiles, final ContentNode node) {
		assertEquals(rootDir.getName(), node.getTitle());
		assertEquals(expectedFiles, getFiles(node.getCopyOfItems()));
	}

	private static void assertContainerContainsDirsAndFiles(final ContentNode node, final List<File> dirs,
			final List<File> files) {
		final Collection<String> expectedDirs = CollectionHelper.map(dirs, new Function<File, String>() {
			@Override
			public String exec(final File input) {
				return input.getName();
			}
		}, new ArrayList<String>());
		final Collection<String> actualDirs = CollectionHelper.map(node.getCopyOfNodes(),
				new Function<ContentNode, String>() {
					@Override
					public String exec(final ContentNode input) {
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
		final Collection<String> actualFiles = CollectionHelper.map(node.getCopyOfItems(), new Function<ContentItem, String>() {
			@Override
			public String exec(final ContentItem input) {
				return input.getTitle();
			}
		}, new ArrayList<String>());
		assertEquals(expectedFiles, actualFiles);
	}

}

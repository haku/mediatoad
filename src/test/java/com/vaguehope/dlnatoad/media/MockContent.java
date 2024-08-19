package com.vaguehope.dlnatoad.media;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.util.FileHelper;

public class MockContent {

	private final ContentTree contentTree;
	private final TemporaryFolder tmp;
	private final AtomicInteger idGen = new AtomicInteger(0);

	private boolean shuffle = true;
	private boolean spy = true;

	public MockContent (final ContentTree contentTree) {
		this(contentTree, null);
	}

	public MockContent (final ContentTree contentTree, final TemporaryFolder tmp) {
		this.contentTree = contentTree;
		this.tmp = tmp;
	}

	public void setShuffle(final boolean shuffle) {
		this.shuffle = shuffle;
	}
	public void setSpy(final boolean spy) {
		this.spy = spy;
	}

	public List<ContentNode> givenMockDirs (final int n) throws IOException {
		final List<ContentNode> ret = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ret.add(addMockDir("dir " + this.idGen.getAndIncrement()));
		}
		return ret;
	}

	public ContentItem givenMockItem() throws IOException {
		return givenMockItems(MediaFormat.MP4, 1).iterator().next();
	}

	public List<ContentItem> givenMockItems (final int n) throws IOException {
		return givenMockItems(MediaFormat.MP4, n);
	}

	public List<ContentItem> givenMockItems (final int n, final Consumer<File> modifier) throws IOException {
		return givenMockItems(MediaFormat.MP4, n, this.contentTree.getRootNode(), modifier);
	}

	public List<ContentItem> givenMockItems (final MediaFormat format, final int n) throws IOException {
		return givenMockItems(format, n, this.contentTree.getRootNode(), null);
	}

	public List<ContentItem> givenMockItems (final int n, final ContentNode parent) throws IOException {
		return givenMockItems(MediaFormat.MP4, n, parent, null);
	}

	public List<ContentItem> givenMockItems (final MediaFormat format, final int n, final ContentNode parent) throws IOException {
		return givenMockItems(format, n, parent, null);
	}

	public List<ContentItem> givenMockItems (final MediaFormat format, final int n, final ContentNode parent, final Consumer<File> modifier) throws IOException {
		final List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ids.add(this.idGen.getAndIncrement());
		}
		if (this.shuffle) Collections.shuffle(ids);

		final List<ContentItem> ret = new ArrayList<>();
		for (final Integer i : ids) {
			final String id = "id" + String.format("%0" + String.valueOf(n).length() + "d", i);
			ret.add(addMockItem(format, id, parent, modifier));
		}
		Collections.sort(ret, ContentItem.Order.ID);

		return ret;
	}

	public ContentNode addMockDir (final String id) throws IOException {
		return addMockDir(id, this.contentTree.getRootNode(), null);
	}

	public ContentNode addMockDir (final String id, final ContentNode parent) throws IOException {
		return addMockDir(id, parent, null);
	}

	public ContentNode addMockDir (final String id, final AuthList authlist) throws IOException {
		return addMockDir(id, this.contentTree.getRootNode(), authlist);
	}

	public ContentNode addMockDir (final String id, final ContentNode parent, final AuthList authlist) throws IOException {
		final File dir;
		final String path;
		if (this.tmp != null) {
			if (parent.getFile() != null) {
				dir = new File(parent.getFile(), id);
			}
			else {
				dir = this.tmp.newFolder(id);
			}
			path = FileHelper.rootAndPath(this.tmp.getRoot(), dir);
		}
		else {
			dir = null;
			path = null;
		}

		final ContentNode node = new ContentNode(id.replace(" ", ""), parent.getId(), id, dir, path, authlist, null);
		this.contentTree.addNode(node);
		parent.addNodeIfAbsent(node);
		return node;
	}

	public ContentItem addMockItem (final String id, final ContentNode parent) throws IOException {
		return addMockItem(MediaFormat.MP4, id, parent, null);
	}

	public ContentItem addMockItem (final MediaFormat format, final String id, final ContentNode parent, final Consumer<File> modifier) throws IOException {
		final String fileName = id + "." + format.getExt();
		final File file;
		if (this.tmp != null) {
			file = spy(this.tmp.newFile(fileName));
		}
		else {
			file = mock(File.class);
			when(file.exists()).thenReturn(true);
			when(file.getName()).thenReturn(fileName);
			when(file.getAbsolutePath()).thenReturn("/mock/path/" + fileName);
		}
		if (modifier != null) modifier.accept(file);

		ContentItem item = new ContentItem(id, parent.getId(), "item " + id, file, format);
		if (this.spy) item = spy(item);
		this.contentTree.addItem(item);
		parent.addItemIfAbsent(item);
		return item;
	}

	public static List<String> contentIds(final Collection<? extends AbstractContent> input) {
		if (input == null) return Collections.emptyList();
		final List<String> ret = new ArrayList<>();
		for (final AbstractContent c : input) {
			ret.add(c.getId());
		}
		return ret;
	}

}

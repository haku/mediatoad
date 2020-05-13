package com.vaguehope.dlnatoad.dlnaserver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.media.MediaFormat;

public class MockContent {

	private final ContentTree contentTree;
	private final TemporaryFolder tmp;

	private boolean shuffle = true;

	public MockContent (final ContentTree contentTree) {
		this(contentTree, null);
	}

	public MockContent (final ContentTree contentTree, final TemporaryFolder tmp) {
		this.contentTree = contentTree;
		this.tmp = tmp;
	}

	public void setShuffle(boolean shuffle) {
		this.shuffle = shuffle;
	}

	public List<ContentNode> givenMockDirs (final int n) throws IOException {
		final List<ContentNode> ret = new ArrayList<ContentNode>();
		for (int i = 0; i < n; i++) {
			ret.add(addMockDir("dir " + i));
		}
		return ret;
	}

	public List<ContentNode> givenMockItems (final int n) throws IOException {
		return givenMockItems(Item.class, n);
	}

	public List<ContentNode> givenMockItems (final int n, Consumer<File> modifier) throws IOException {
		return givenMockItems(Item.class, n, this.contentTree.getRootNode(), modifier);
	}

	public List<ContentNode> givenMockItems (final Class<? extends Item> cls, final int n) throws IOException {
		return givenMockItems(cls, n, this.contentTree.getRootNode(), null);
	}

	public List<ContentNode> givenMockItems (final int n, final ContentNode parent) throws IOException {
		return givenMockItems(Item.class, n, parent, null);
	}

	public List<ContentNode> givenMockItems (final Class<? extends Item> cls, final int n, final ContentNode parent, Consumer<File> modifier) throws IOException {
		final List<Integer> ids = new ArrayList<Integer>();
		for (int i = 0; i < n; i++) {
			ids.add(i);
		}
		if (this.shuffle) Collections.shuffle(ids);

		final List<ContentNode> ret = new ArrayList<ContentNode>();
		for (final Integer i : ids) {
			final String id = "id" + String.format("%0" + String.valueOf(n).length() + "d", i);
			ret.add(addMockItem(cls, id, parent, modifier));
		}
		Collections.sort(ret, ContentNode.Order.ID);

		return ret;
	}

	public ContentNode addMockDir (final String id) throws IOException {
		return addMockDir(id, this.contentTree.getRootNode());
	}

	public ContentNode addMockDir (final String id, final ContentNode parent) throws IOException {
		final Container container = new Container();
		container.setId(id);
		container.setTitle(id);
		container.setChildCount(Integer.valueOf(0));

		final File dir;
		if (this.tmp != null) {
			dir = this.tmp.newFolder(id);
		}
		else {
			dir = null;
		}

		final ContentNode node = new ContentNode(id, container, dir);
		this.contentTree.addNode(node);
		parent.addChild(node);
		return node;
	}

	public ContentNode addMockItem (final String id, final ContentNode parent) throws IOException {
		return addMockItem(Item.class, id, parent, null);
	}

	public ContentNode addMockItem (final Class<? extends Item> cls, final String id, final ContentNode parent, Consumer<File> modifier) throws IOException {
		final MediaFormat format = MediaFormat.MP4;
		final Res res = mock(Res.class);

		final Item item = mock(cls);
		when(item.getId()).thenReturn(id);
		when(item.getTitle()).thenReturn("item " + id);
		when(item.toString()).thenReturn("item " + id);
		when(item.getResources()).thenReturn(Arrays.asList(res));

		final String fileName = id + "." + format.getExt();
		final File file;
		if (this.tmp != null) {
			file = this.tmp.newFile(fileName);
		}
		else {
			file = mock(File.class);
			when(file.exists()).thenReturn(true);
			when(file.getName()).thenReturn(fileName);
			when(file.getAbsolutePath()).thenReturn("/mock/path/" + fileName);
		}
		if (modifier != null) modifier.accept(file);
		final ContentNode node = new ContentNode(id, item, file, format);
		this.contentTree.addNode(node);
		parent.addChild(node);
		return node;
	}

	/**
	 * Collect up containers from nodes (not child containers).
	 */
	public static List<Container> nodeContainers (final Collection<ContentNode> nodes) {
		final List<Container> l = new ArrayList<Container>();
		if (nodes != null) {
			for (final ContentNode cn : nodes) {
				// This breaks the locking, but (probably) not important in unit tests.
				if (cn.hasContainer()) cn.withContainer(c -> l.add(c));
			}
		}
		return l;
	}

	public static List<Container> childContainers (final ContentNode... nodes) {
		return childContainers(Arrays.asList(nodes));
	}

	/**
	 * Collect up child containers from nodes.
	 */
	public static List<Container> childContainers (final Collection<ContentNode> nodes) {
		final List<Container> l = new ArrayList<Container>();
		if (nodes != null) {
			for (final ContentNode cn : nodes) {
				// This breaks the locking, but (probably) not important in unit tests.
				cn.withEachChildContainer(c -> l.add(c));
			}
		}
		return l;
	}

	public static List<Item> nodeItems (final Collection<ContentNode> nodes) {
		final List<Item> l = new ArrayList<Item>();
		if (nodes != null) {
			for (final ContentNode cn : nodes) {
				// This breaks the locking, but (probably) not important in unit tests.
				if (cn.hasItem()) cn.withItem(i -> l.add(i));
			}
		}
		return l;
	}

}

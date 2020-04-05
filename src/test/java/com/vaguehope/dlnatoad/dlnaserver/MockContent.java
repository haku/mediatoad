package com.vaguehope.dlnatoad.dlnaserver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

	public MockContent (final ContentTree contentTree) {
		this(contentTree, null);
	}

	public MockContent (final ContentTree contentTree, final TemporaryFolder tmp) {
		this.contentTree = contentTree;
		this.tmp = tmp;
	}

	public List<ContentNode> givenMockDirs (final int n) {
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
		final List<ContentNode> ret = new ArrayList<ContentNode>();
		for (int i = 0; i < n; i++) {
			ret.add(addMockItem(cls, "id" + i, parent, modifier));
		}
		return ret;
	}

	public ContentNode addMockDir (final String id) {
		return addMockDir(id, this.contentTree.getRootNode());
	}

	public ContentNode addMockDir (final String id, final ContentNode parent) {
		final Container container = new Container();
		container.setId(id);
		container.setChildCount(Integer.valueOf(0));

		final ContentNode node = new ContentNode(id, container);
		this.contentTree.addNode(node);
		parent.getContainer().addContainer(node.getContainer());
		parent.getContainer().setChildCount(parent.getContainer().getChildCount() + 1);
		return node;
	}

	public ContentNode addMockItem (final String id, final ContentNode parent) throws IOException {
		return addMockItem(Item.class, id, parent, null);
	}

	public ContentNode addMockItem (final Class<? extends Item> cls, final String id, final ContentNode parent, Consumer<File> modifier) throws IOException {
		final Res res = mock(Res.class);

		final Item item = mock(cls);
		when(item.getId()).thenReturn(id);
		when(item.getTitle()).thenReturn("item " + id);
		when(item.toString()).thenReturn("item " + id);
		when(item.getResources()).thenReturn(Arrays.asList(res));

		final String fileName = id + ".mp4";
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
		final ContentNode node = new ContentNode(id, item, file, MediaFormat.OGG);
		this.contentTree.addNode(node);
		parent.getContainer().addItem(node.getItem());
		parent.getContainer().setChildCount(parent.getContainer().getChildCount() + 1);
		return node;
	}

	public static List<Container> listOfContainers (final Collection<ContentNode> nodes) {
		final List<Container> l = new ArrayList<Container>();
		if (nodes != null) {
			for (final ContentNode cn : nodes) {
				final Container container = cn.getContainer();
				if (container != null) l.add(container);
			}
		}
		return l;
	}

	public static List<Item> listOfItems (final Collection<ContentNode> nodes) {
		final List<Item> l = new ArrayList<Item>();
		if (nodes != null) {
			for (final ContentNode cn : nodes) {
				final Item item = cn.getItem();
				if (item != null) l.add(item);
			}
		}
		return l;
	}

}

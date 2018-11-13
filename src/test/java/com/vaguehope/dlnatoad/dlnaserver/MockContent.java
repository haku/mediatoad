package com.vaguehope.dlnatoad.dlnaserver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.media.MediaFormat;

public class MockContent {

	private final ContentTree contentTree;

	public MockContent (final ContentTree contentTree) {
		this.contentTree = contentTree;
	}

	public List<ContentNode> givenMockDirs (final int n) {
		final List<ContentNode> ret = new ArrayList<ContentNode>();
		for (int i = 0; i < n; i++) {
			ret.add(addMockDir("dir " + i));
		}
		return ret;
	}

	public List<ContentNode> givenMockItems (final int n) {
		return givenMockItems(Item.class, n);
	}

	public List<ContentNode> givenMockItems (final Class<? extends Item> cls, final int n) {
		return givenMockItems(cls, n, this.contentTree.getRootNode());
	}

	public List<ContentNode> givenMockItems (final int n, final ContentNode parent) {
		return givenMockItems(Item.class, n, parent);
	}

	public List<ContentNode> givenMockItems (final Class<? extends Item> cls, final int n, final ContentNode parent) {
		final List<ContentNode> ret = new ArrayList<ContentNode>();
		for (int i = 0; i < n; i++) {
			ret.add(addMockItem(cls, "item " + i, parent));
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

	public ContentNode addMockItem (final String id, final ContentNode parent) {
		return addMockItem(Item.class, id, parent);
	}

	public ContentNode addMockItem (final Class<? extends Item> cls, final String id, final ContentNode parent) {
		final Item item = mock(cls);
		when(item.getTitle()).thenReturn("item " + id);
		when(item.toString()).thenReturn("item " + id);
		final ContentNode node = new ContentNode(id, item, mock(File.class), MediaFormat.OGG);
		this.contentTree.addNode(node);
		parent.getContainer().addItem(node.getItem());
		parent.getContainer().setChildCount(parent.getContainer().getChildCount() + 1);
		return node;
	}

	public static List<Container> listOfContainers (final List<ContentNode> nodes) {
		final List<Container> l = new ArrayList<Container>();
		if (nodes != null) {
			for (final ContentNode cn : nodes) {
				l.add(cn.getContainer());
			}
		}
		return l;
	}

	public static List<Item> listOfItems (final List<ContentNode> nodes) {
		final List<Item> l = new ArrayList<Item>();
		if (nodes != null) {
			for (final ContentNode cn : nodes) {
				l.add(cn.getItem());
			}
		}
		return l;
	}

}

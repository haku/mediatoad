package com.vaguehope.dlnatoad.dlnaserver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.WriteStatus;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentTree {

	private static final Logger LOG = LoggerFactory.getLogger(ContentTree.class);

	private final Map<String, ContentNode> contentMap = new ConcurrentHashMap<String, ContentNode>();
	private final ContentNode rootNode;

	private static final int MAX_RECENT_ITEMS = 50;
	private final SetBackedContainer recentContainer;
	private final NavigableSet<ContentNode> recent = new ConcurrentSkipListSet<ContentNode>(ContentNode.Order.MODIFIED_DESC);
	private final Object[] recentLock = new Object[] {};
	private volatile long oldestRecentItem = 0L;

	public ContentTree () {
		this(true);
	}

	public ContentTree (final boolean trackRecent) {
		this.rootNode = createRootNode();
		this.contentMap.put(ContentGroup.ROOT.getId(), this.rootNode);

		if (trackRecent) {
			this.recentContainer = createRecentContainer();
			this.contentMap.put(this.recentContainer.getId(), new ContentNode(this.recentContainer.getId(), this.recentContainer));
			final Container rc = rootNode.getContainer();
			rc.addContainer(recentContainer);
			rc.setChildCount(Integer.valueOf(rc.getChildCount().intValue() + 1));
		}
		else {
			this.recentContainer = null;
		}
	}

	private static ContentNode createRootNode () {
		final Container root = new Container();
		root.setClazz(new DIDLObject.Class("object.container"));
		root.setId(ContentGroup.ROOT.getId());
		root.setParentID("-1");
		root.setTitle(ContentGroup.ROOT.getHumanName());
		root.setCreator(C.METADATA_MODEL_NAME);
		root.setRestricted(true);
		root.setSearchable(true);
		root.setWriteStatus(WriteStatus.NOT_WRITABLE);
		root.setChildCount(Integer.valueOf(0));
		return new ContentNode(ContentGroup.ROOT.getId(), root);
	}

	private SetBackedContainer createRecentContainer () {
		final SetBackedContainer c = new SetBackedContainer(this.recent);
		c.setClazz(new DIDLObject.Class("object.container"));
		c.setId(ContentGroup.RECENT.getId());
		c.setParentID(rootNode.getId());
		c.setTitle(ContentGroup.RECENT.getHumanName());
		c.setRestricted(true);
		c.setWriteStatus(WriteStatus.NOT_WRITABLE);
		c.setChildCount(Integer.valueOf(0));
		return c;
	}

	private static boolean isValidItem (final ContentNode node) {
		return node.getFile() != null && node.getFile().exists();
	}

	public int getNodeCount() {
		return this.contentMap.size();
	}

	public ContentNode getRootNode () {
		return this.rootNode;
	}

	public Collection<ContentNode> getNodes () {
		return this.contentMap.values();
	}

	public ContentNode getNode (final String id) {
		return this.contentMap.get(id);
	}

	public void addNode (final ContentNode node) {
		this.contentMap.put(node.getId(), node);
		maybeAddToRecent(node);
	}

	/**
	 * Returns number of items removed.
	 */
	public int removeFile (final File file) {
		if (file == null) return 0;
		// FIXME this is lazy and not efficient.
		final List<ContentNode> toRemove = new ArrayList<>();
		for (final Entry<String, ContentNode> e : this.contentMap.entrySet()) {
			if (file.equals(e.getValue().getFile())) toRemove.add(e.getValue());
		}
		for (final ContentNode node : toRemove) {
			removeNode(node);
		}
		return toRemove.size();
	}

	public void removeNode (final ContentNode node) {
		if (node.isItem()) {
			this.contentMap.remove(node.getId());
			if (node.getItem() != null) {
				final ContentNode parentNode = this.contentMap.get(node.getItem().getParentID());
				if (parentNode != null) {
					removeItemFromContainer(parentNode.getContainer(), node.getItem());
					if (removeContainerFromItsParentIfEmpty(parentNode.getContainer())) {
						this.contentMap.remove(parentNode.getId());
					}
				}
			}
		}
		else if (node.getContainer() != null) {
			removeContainerFromItsParent(node.getContainer());
		}
		else {
			throw new IllegalArgumentException("Not an item or a container: " + node);
		}
		removeFromRecent(node);
	}

	public void prune () {
		final Iterator<ContentNode> ittr = this.contentMap.values().iterator();
		while (ittr.hasNext()) {
			final ContentNode node = ittr.next();
			if (node.isItem()) {
				if (!isValidItem(node)) {
					ittr.remove();
					removeFromRecent(node);
					LOG.info("unshared: {}", node.getFile().getAbsolutePath());
				}
			}
			else {
				final Container c = node.getContainer();
				pruneItems(c);
				if (removeContainerFromItsParentIfEmpty(c)) ittr.remove();
			}
		}
	}

	private void pruneItems (final Container c) {
		synchronized (c) {
			final Iterator<Item> it = c.getItems().iterator();
			while (it.hasNext()) {
				final Item item = it.next();
				final ContentNode itemNode = this.contentMap.get(item.getId());
				if (itemNode == null || !isValidItem(itemNode)) it.remove();
			}
			c.setChildCount(c.getContainers().size() + c.getItems().size());
		}
	}

	private boolean removeContainerFromItsParentIfEmpty (final Container c) {
		if (c.getChildCount() < 1 && !ContentGroup.incluesId(c.getId())) {
			removeContainerFromItsParent(c);
			return true;
		}
		return false;
	}

	private void removeContainerFromItsParent (final Container c) {
		final ContentNode parentNode = this.contentMap.get(c.getParentID());
		if (parentNode != null) {
			final Container parentC = parentNode.getContainer();
			synchronized (parentC) {
				if (parentC.getContainers().remove(c)) {
					parentC.setChildCount(Integer.valueOf(parentC.getChildCount() - 1));
				}
				else {
					LOG.error("Container '{}' not in its parent '{}'.", c.getId(), parentC.getId());
				}
			}
		}
		else {
			LOG.error("Parent of container '{}' not found in contentMap: '{}'.", c.getId(), c.getParentID());
		}
	}

	private static void removeItemFromContainer (final Container c, final Item itemToRemove) {
		synchronized (c) {
			final Iterator<Item> it = c.getItems().iterator();
			while (it.hasNext()) {
				final Item item = it.next();
				if (itemToRemove.getId().equals(item.getId())) it.remove();
			}
			c.setChildCount(c.getContainers().size() + c.getItems().size());
		}
	}

	public Collection<ContentNode> getRecent () {
		return this.recent;
	}

	private void maybeAddToRecent(final ContentNode node) {
		if (this.recentContainer == null) return;
		if (!node.hasItem()) return;

		if (node.getLastModified() < this.oldestRecentItem) return;

		synchronized (this.recentLock) {
			this.recent.add(node);
			if (this.recent.size() > MAX_RECENT_ITEMS) {
				this.recent.pollLast();
				this.oldestRecentItem = this.recent.last().getLastModified();
			}
			else {
				this.oldestRecentItem = 0L;
			}
			recentContainer.reload();
		}
	}

	private void removeFromRecent(final ContentNode node) {
		if (this.recentContainer == null) return;

		synchronized (this.recentLock) {
			if (this.recent.remove(node)) {
				this.recentContainer.reload();
			}
		}
	}

	private static class SetBackedContainer extends Container {

		private final Set<ContentNode> set;

		public SetBackedContainer(final Set<ContentNode> set) {
			this.set = set;
		}

		public void reload () {
			final List<Item> newItems = new ArrayList<Item>(set.size());
			for (final ContentNode cn : this.set) {
				if (cn.hasItem()) {
					newItems.add(cn.getItem());
				}
			}
			this.items = newItems;
		}

	    @Override
		public void setItems(final List<Item> items) {
	        throw new UnsupportedOperationException();
	    }

	    @Override
		public Container addItem(final Item item) {
	    	throw new UnsupportedOperationException();
	    }

	}

}

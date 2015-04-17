package com.vaguehope.dlnatoad.dlnaserver;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.support.model.DIDLObject;
import org.teleal.cling.support.model.WriteStatus;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.C;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentTree {

	private static final Logger LOG = LoggerFactory.getLogger(ContentTree.class);

	private final Map<String, ContentNode> contentMap;
	private final ContentNode rootNode;

	public ContentTree () {
		this.contentMap = new ConcurrentHashMap<String, ContentNode>();
		this.rootNode = createRootNode();
		this.contentMap.put(ContentGroup.ROOT.getId(), this.rootNode);
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

	private static boolean isValidItem (final ContentNode node) {
		return node.getFile() != null && node.getFile().exists();
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
	}

	public void prune () {
		final Iterator<ContentNode> ittr = this.contentMap.values().iterator();
		while (ittr.hasNext()) {
			final ContentNode node = ittr.next();
			if (node.isItem()) {
				if (!isValidItem(node)) {
					ittr.remove();
					LOG.info("unshared: {}", node.getFile().getAbsolutePath());
				}
			}
			else {
				final Container c = node.getContainer();
				pruneItems(c);
				if (c.getChildCount() < 1 && !ContentGroup.incluesId(c.getId())) {
					removeContainerFromItsParent(c);
					ittr.remove();
				}
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

}

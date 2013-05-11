package com.vaguehope.dlnatoad.dlnaserver;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	public static final String ROOT_ID = "0"; // Root id of '0' is in the spec.
	public static final String VIDEO_ID = "1-videos";
	public static final String IMAGE_ID = "2-images";
	public static final String AUDIO_ID = "3-audio";
	public static final String VIDEO_PREFIX = "video-";
	public static final String IMAGE_PREFIX = "images-";
	public static final String AUDIO_PREFIX = "audio-";

	private final Map<String, ContentNode> contentMap;
	private final ContentNode rootNode;

	public ContentTree () {
		this.contentMap = new ConcurrentHashMap<String, ContentNode>();
		this.rootNode = createRootNode();
		this.contentMap.put(ROOT_ID, this.rootNode);
	}

	private static ContentNode createRootNode () {
		final Container root = new Container();
		root.setId(ROOT_ID);
		root.setParentID("-1");
		root.setTitle(C.CONTENT_ROOT_DIR);
		root.setCreator(C.METADATA_MODEL_NAME);
		root.setRestricted(true);
		root.setSearchable(true);
		root.setWriteStatus(WriteStatus.NOT_WRITABLE);
		root.setChildCount(Integer.valueOf(0));
		return new ContentNode(ROOT_ID, root);
	}

	private static boolean isDefault (final Container c) {
		return ROOT_ID.equals(c.getId())
				|| VIDEO_ID.equals(c.getId())
				|| IMAGE_ID.equals(c.getId())
				|| AUDIO_ID.equals(c.getId());
	}

	private static boolean isValidItem (final ContentNode node) {
		return node.getFile() != null && node.getFile().exists();
	}

	public ContentNode getRootNode () {
		return this.rootNode;
	}

	public int itemCount () {
		int n = 0;
		for (final ContentNode node : this.contentMap.values()) {
			if (node.isItem()) n += 1;
		}
		return n;
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
		for (ContentNode node : this.contentMap.values()) {
			if (node.isItem()) {
				if (!isValidItem(node)) removeNode(node);
			}
			else {
				Container c = node.getContainer();
				pruneItems(c);
				if (c.getChildCount() < 1 && !isDefault(c)) removeNode(node);
			}
		}
	}

	private void removeNode (final ContentNode node) {
		this.contentMap.remove(node.getId());
		LOG.info("unshared: {}", node);
	}

	private void pruneItems (final Container c) {
		Iterator<Item> it = c.getItems().iterator();
		while (it.hasNext()) {
			Item item = it.next();
			ContentNode itemNode = this.contentMap.get(item.getId());
			if (itemNode == null || !isValidItem(itemNode)) it.remove();
		}
		c.setChildCount(c.getContainers().size() + c.getItems().size());
	}

}

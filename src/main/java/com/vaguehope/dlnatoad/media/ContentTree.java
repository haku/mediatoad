package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.AuthSet;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentTree {

	// NOTE: this will fail is more than once instance of Watcher exists.
	// if that is ever needed, will need to keep track of instances and add queue depths together.
	private final GaugeWithCallback nodeCountMetric = GaugeWithCallback.builder()
			.name("content_node_count")
			.help("number of directories in the index.")
			.callback((cb) -> cb.call(this.contentNodes.size()))
			.build();
	private final GaugeWithCallback itemCountMetric = GaugeWithCallback.builder()
			.name("content_item_count")
			.help("number of files in the index.")
			.callback((cb) -> cb.call(this.contentItems.size()))
			.build();

	private static final Logger LOG = LoggerFactory.getLogger(ContentTree.class);

	private final AuthSet authSet = new AuthSet();
	private final Map<String, ContentNode> contentNodes = new ConcurrentHashMap<>();
	private final Map<String, ContentNode> contentNodePaths = new ConcurrentHashMap<>();
	private final ContentNode rootNode;
	private final Map<String, ContentItem> contentItems = new ConcurrentHashMap<>();

	private final RecentContentNode recentNode;

	public ContentTree() {
		this(true);
	}

	public ContentTree (final boolean trackRecent) {
		this.rootNode = new ContentNode(ContentGroup.ROOT.getId(), "-1", ContentGroup.ROOT.getHumanName(), C.METADATA_MODEL_NAME);
		addNode(this.rootNode);

		if (trackRecent) {
			this.recentNode = new RecentContentNode();
			// TODO mark recent as not searchable.
			addNode(this.recentNode);
			this.rootNode.addNodeIfAbsent(this.recentNode);
		}
		else {
			this.recentNode = null;
		}
	}

	public void registerMetrics(final PrometheusRegistry registry) {
		registry.register(this.nodeCountMetric);
		registry.register(this.itemCountMetric);
	}

	public AuthSet getAuthSet() {
		return this.authSet;
	}

	public int getNodeCount() {
		return this.contentNodes.size();
	}

	public int getItemCount() {
		return this.contentItems.size();
	}

	public ContentNode getRootNode () {
		return this.rootNode;
	}

	Collection<ContentNode> getNodes () {
		return this.contentNodes.values();
	}

	public Collection<ContentItem> getItems() {
		return this.contentItems.values();
	}

	public ContentNode getNode (final String id) {
		if (id == null) throw new NullPointerException("Cannot get node with null id.");
		return this.contentNodes.get(id);
	}

	public void addNode (final ContentNode node) {
		this.authSet.add(node.getAuthList());
		this.contentNodes.put(node.getId(), node);
		addNodePath(node);
	}

	public ContentNode getNodeByPath(final String path) {
		return this.contentNodePaths.get(path);
	}

	Set<String> getNodePaths() {
		return this.contentNodePaths.keySet();
	}

	private void addNodePath(final ContentNode node) {
		if (node.getPath() == null) return;
		final ContentNode existing = this.contentNodePaths.putIfAbsent(node.getPath(), node);
		if (existing != null) LOG.warn("Dropping '{}' from pathmap because it has same path as '{}' relative to their respective roots: '{}'",
				node.getFile().getAbsolutePath(), existing.getFile().getAbsolutePath(), existing.getPath());
	}

	private void removeNodePath(final ContentNode node) {
		if (node.getPath() == null) return;
		this.contentNodePaths.remove(node.getPath(), node);
	}

	public ContentItem getItem(final String id) {
		if (id == null) throw new NullPointerException("Cannot get item with null id.");
		return this.contentItems.get(id);
	}

	public void addItem(final ContentItem item) {
		this.contentItems.put(item.getId(), item);

		if (this.recentNode != null) {
			// Do not add items in collections that require auth.
			// For safety missing content nodes are assumed to have an auth list.
			final ContentNode node = getNode(item.getParentId());
			if (node != null) {
				this.recentNode.maybeAddToRecent(item, node);
			}
			else {
				LOG.warn("Item {} has parent {} which is not in the content tree.", item.getId(), item.getParentId());
			}
		}
	}

	public List<ContentItem> getItemsForIds(final Collection<String> ids, final String username) {
		final List<ContentItem> ret = new ArrayList<>();
		for (final String id : ids) {
			final ContentItem item = getItem(id);
			if (item == null) {
				LOG.debug("ID not found in content tree: {}", id);
				continue;
			}
			final ContentNode node = getNode(item.getParentId());
			if (!node.isUserAuth(username)) continue;
			ret.add(item);
		}
		return ret;
	}

	/**
	 * Returns number of items removed.
	 */
	public int removeFile (final File file) {
		if (file == null) throw new IllegalArgumentException("file can not be null.");
		int removeCount = 0;

		// FIXME this is very lazy and not efficient.

		final Iterator<Entry<String, ContentNode>> nodeIttr = this.contentNodes.entrySet().iterator();
		while (nodeIttr.hasNext()) {
			final Entry<String, ContentNode> e = nodeIttr.next();
			if (file.equals(e.getValue().getFile())) {
				nodeIttr.remove();
				removeNodePath(e.getValue());
				removeNodesAndItemsInNode(e.getValue());
				removeNodeFromParent(e.getValue());
				removeCount += 1;
			}
		}

		final Iterator<Entry<String, ContentItem>> itemIttr = this.contentItems.entrySet().iterator();
		while (itemIttr.hasNext()) {
			final Entry<String, ContentItem> e = itemIttr.next();
			if (file.equals(e.getValue().getFile())) {
				itemIttr.remove();
				removeItemFromParent(e.getValue());
				if (this.recentNode != null) this.recentNode.removeFromRecent(e.getValue());
				removeCount += 1;
			}
		}

		return removeCount;
	}

	private void removeNodesAndItemsInNode(final ContentNode node) {
		node.withEachNode((n) -> {
			this.contentNodes.remove(n.getId());
			removeNodePath(n);

			// in theory this recursively getting a lock on a node via withEachNode() while within a lock
			// could deadlock, but lets see if that ever happens in practice before switching to a list copy.
			removeNodesAndItemsInNode(n);
		});
		node.withEachItem((i) -> {
			this.contentItems.remove(i.getId());
		});
	}

	private void removeNodeFromParent(final ContentNode node) {
		final ContentNode parentNode = this.contentNodes.get(node.getParentId());
		if (parentNode == null) {
			LOG.error("Parent of container '{}' not found in contentMap: '{}'.", node.getId(), node.getParentId());
			return;
		}
		if (!parentNode.removeNode(node)) {
			LOG.error("Container '{}' not in its parent: '{}'.", node.getId(), node.getParentId());
		}
		if (isContainerEmptyAndRemoveable(parentNode)) {
			this.contentNodes.remove(parentNode.getId());
			removeNodePath(parentNode);
			removeNodeFromParent(parentNode);
		}
	}

	private static boolean isContainerEmptyAndRemoveable(final ContentNode node) {
		return node.getNodeAndItemCount() < 1 && !ContentGroup.incluesId(node.getId());
	}

	private void removeItemFromParent(final ContentItem item) {
		if (item.getParentId() == null) return;

		final ContentNode parentNode = this.contentNodes.get(item.getParentId());
		if (parentNode == null) {
			LOG.error("Parent of item '{}' not found in contentMap: '{}'.", item.getId(), item.getParentId());
			return;
		}
		if (!parentNode.removeItem(item)) {
			LOG.error("Item '{}' not in its parent: '{}'.", item.getId(), item.getParentId());
		}
		if (isContainerEmptyAndRemoveable(parentNode)) {
			this.contentNodes.remove(parentNode.getId());
			removeNodePath(parentNode);
			removeNodeFromParent(parentNode);
		}
	}

	public RecentContentNode getRecent () {
		return this.recentNode;
	}

}

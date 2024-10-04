package com.vaguehope.dlnatoad.media;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.auth.Permission;
import com.vaguehope.dlnatoad.fs.MediaFile;
import com.vaguehope.dlnatoad.util.ExConsumer;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentNode extends AbstractContent {

	private final String sortKey;
	private final MediaFile file;
	private final String path;
	private final AuthList authList;

	private final List<ContentNode> nodes = new ArrayList<>();
	private final Collection<ContentItem> items;

	private volatile ContentItem art;
	private volatile long lastModified = 0L;

	public ContentNode (final String id, final String parentId, final String title, final String sortKey) {
		this(id, parentId, title, null, null, null, sortKey);
	}

	public ContentNode (final String id, final String parentId, final String title, final MediaFile dir, final String path, final AuthList authList, final String sortKey) {
		this(id, parentId, title, dir, path, authList, sortKey, new ArrayList<>());
	}

	public ContentNode (final String id, final String parentId, final String title, final MediaFile dir, final String path, final AuthList authList, final String sortKey, final Collection<ContentItem> itemsCollection) {
		super(id, parentId, title);
		if (parentId == null)  throw new IllegalArgumentException("parentId must not be null.");
		this.file = dir;
		this.path = path;
		this.authList = authList;
		this.sortKey = sortKey;
		this.items = itemsCollection;
		reload();
	}

	public AuthList getAuthList() {
		return this.authList;
	}

	public BigInteger getAuthId() {
		if (this.authList == null) return BigInteger.ZERO;
		return this.authList.getId();
	}

	public boolean hasAuthList() {
		return this.authList != null;
	}

	public boolean isUserAuth(final String username) {
		if (this.authList == null) return true;
		return this.authList.hasUser(username);
	}

	public boolean isUserAuthWithPermission(final String username, final Permission permission) {
		if (this.authList == null) return false;
		return this.authList.hasUserWithPermission(username, permission);
	}

	public String getSortKey() {
		return this.sortKey;
	}

	public List<ContentNode> nodesUserHasAuth(final String username) {
		synchronized (this.nodes) {
			final List<ContentNode> ret = new ArrayList<>(this.nodes.size());
			for (final ContentNode node : this.nodes) {
				if (node.isUserAuth(username)) ret.add(node);
			}
			return ret;
		}
	}

	public <E extends Exception> void withEachNode (final ExConsumer<ContentNode, E> consumer) throws E {
		synchronized (this.nodes) {
			for (final ContentNode node : this.nodes) {
				consumer.accept(node);
			}
		}
	}

	public <E extends Exception> void withEachItem (final ExConsumer<ContentItem, E> consumer) throws E {
		synchronized (this.items) {
			for (final ContentItem item : this.items) {
				consumer.accept(item);
			}
		}
	}

	public void setArt(final ContentItem art) {
		this.art = art;
	}

	public ContentItem getArt() {
		return this.art;
	}

	public MediaFile getFile () {
		return this.file;
	}

	public String getPath() {
		return this.path;
	}

	public long getLastModified() {
		return this.lastModified;
	}

	public int getNodeAndItemCount() {
		return getNodeCount() + getItemCount();
	}

	public int getNodeCount() {
		synchronized (this.nodes) {
			return this.nodes.size();
		}
	}

	public int getItemCount() {
		synchronized (this.items) {
			return this.items.size();
		}
	}

	public long getTotalFileLength() {
		long total = 0L;
		synchronized (this.items) {
			for (final ContentItem i : this.items) {
				total += i.getFileLength();
			}
		}
		return total;
	}

	public List<ContentNode> getCopyOfNodes() {
		return new ArrayList<>(this.nodes);
	}

	public List<ContentItem> getCopyOfItems() {
		return new ArrayList<>(this.items);
	}

	public boolean addNodeIfAbsent(final ContentNode node) {
		if (!this.id.equals(node.getParentId())) {
			throw new IllegalArgumentException(String.format(
					"Node %s with parent ID %s can not be added to node with id %s.",
					node.getId(), node.getParentId(), this.id));
		}

		synchronized (this.nodes) {
			if (hasNodeWithId(node.getId())) return false;
			this.nodes.add(node);
			Collections.sort(this.nodes, Order.SORT_KEY);
			return true;
		}
	}

	public boolean addItemIfAbsent(final ContentItem item) {
		if (!this.id.equals(item.getParentId())) {
			throw new IllegalArgumentException(String.format(
					"Item %s with parent ID %s can not be added to node with id %s.",
					item.getId(), item.getParentId(), this.id));
		}

		synchronized (this.items) {
			if (hasItemWithId(item.getId())) return false;
			this.items.add(item);
			if (this.items instanceof List) {
				Collections.sort((List<ContentItem>) this.items, ContentItem.Order.TITLE_CASE_INSENSITIVE);
			}
			return true;
		}
	}

	public boolean removeNode(final ContentNode toRemove) {
		synchronized (this.nodes) {
			return removeById(this.nodes, toRemove.getId());
		}
	}

	public boolean removeItem(final ContentItem toRemove) {
		synchronized (this.items) {
			return removeById(this.items, toRemove.getId());
		}
	}

	private static <T extends AbstractContent> boolean removeById(final Collection<T> list, final String id) {
		final Iterator<T> it = list.iterator();
		boolean removed = false;
		while (it.hasNext()) {
			final T item = it.next();
			if (id.equals(item.getId())) {
				it.remove();
				removed = true;
			}
		}
		return removed;
	}

	public boolean hasNodeWithId(final String idToRemove) {
		synchronized (this.nodes) {
			for (final ContentNode c : this.nodes) {
				if (idToRemove.equals(c.getId())) return true;
			}
		}
		return false;
	}

	public boolean hasItemWithId(final String idToFind) {
		synchronized (this.items) {
			for (final ContentItem i : this.items) {
				if (idToFind.equals(i.getId())) return true;
			}
		}
		return false;
	}

	public void reload() {
		if (this.file == null || !this.file.exists()) return;
		this.lastModified = this.file.lastModified();
	}

	@Override
	public final int hashCode() {
		return this.id.hashCode();
	}

	@Override
	public boolean equals (final Object obj) {
		if (obj == null) return false;
		if (obj == this) return true;
		if (!(obj instanceof ContentNode)) return false;
		final ContentNode that = (ContentNode) obj;
		return Objects.equals(this.id, that.id);
	}

	@Override
	public String toString () {
		final StringBuilder s = new StringBuilder();
		s.append("contaner{");
		s.append(this.id);
		s.append(", ").append(this.parentId);
		s.append(", ").append(this.title);
		s.append(", ");
		synchronized (this.nodes) {
			s.append(this.nodes);
		}
		synchronized (this.items) {
			s.append(this.items);
		}
		return s.append("}").toString();
	}

	public enum Order implements Comparator<ContentNode> {
		MODIFIED_DESC {
			@Override
			public int compare (final ContentNode a, final ContentNode b) {
				final int c = Long.compare(b.lastModified, a.lastModified);
				if (c != 0) return c;
				return TITLE_CASE_INSENSITIVE.compare(a, b);
			}
		},
		SORT_KEY {
			@Override
			public int compare(final ContentNode a, final ContentNode b) {
				final String at = a.sortKey;
				final String bt = b.sortKey;
				if (at == bt) return ID.compare(a, b);
				final int c = (at != null ? (bt != null ? at.compareTo(bt) : 1) : -1);
				if (c != 0) return c;
				return ID.compare(a, b);
			}
		},
		TITLE_CASE_INSENSITIVE {
			@Override
			public int compare(final ContentNode a, final ContentNode b) {
				final String at = a.title;
				final String bt = b.title;
				if (at == bt) ID.compare(a, b);
				final int c = (at != null ? (bt != null ? at.compareToIgnoreCase(bt) : 1) : -1);
				if (c != 0) return c;
				return ID.compare(a, b);
			}
		},
		ID {
			@Override
			public int compare(final ContentNode a, final ContentNode b) {
				return a.id.compareTo(b.id);
			}
		};

		@Override
		public abstract int compare (ContentNode a, ContentNode b);
	}

}

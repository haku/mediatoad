package com.vaguehope.dlnatoad.dlnaserver;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.DIDLObject.Property;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.util.ExConsumer;
import com.vaguehope.dlnatoad.util.ExFunction;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentNode {

	private final Container container;
	private final Item item;
	private final String id;
	private final File file;
	private final boolean isItem;
	private final MediaFormat format;

	private volatile long fileLength = 0L;
	private volatile long lastModified = 0L;

	public ContentNode (final String id, final Container container) {
		this(id, container, null);
	}

	public ContentNode (final String id, final Container container, File dir) {
		if (id == null) throw new IllegalArgumentException("id must not be null.");
		if (container == null) throw new IllegalArgumentException("container must not be null.");
		if (container.getId() == null)  throw new IllegalArgumentException("container must not have null id.");
		this.id = id;
		this.item = null;
		this.container = container;
		this.file = dir;
		this.format = null;
		this.isItem = false;
		synchronized (this.container) {
			updateContainerSize();
		}
		reload();
	}

	public ContentNode (final String id, final Item item, final File file, final MediaFormat format) {
		if (id == null) throw new IllegalArgumentException("id must not be null.");
		// Item can be null.
		this.id = id;
		this.item = item;
		this.container = null;
		this.file = file;
		this.format = format;
		this.isItem = true;
		reload();
	}

	public String getId () {
		return this.id;
	}

	public boolean hasContainer () {
		return this.container != null;
	}

	/**
	 * Synchronised access to the enclosed container.
	 */
	public void withContainer (Consumer<Container> consumer) {
		synchronized (this.container) {
			consumer.accept(this.container);
		}
	}

	/**
	 * Synchronised access to the enclosed container with return value.
	 */
	public <E extends Exception, T> T applyContainer (ExFunction<Container, T, E> fn) throws E {
		synchronized (this.container) {
			return fn.apply(this.container);
		}
	}

	/**
	 * Synchronised access to the enclosed container.
	 */
	public void withItem (Consumer<Item> consumer) {
		synchronized (this.item) {
			consumer.accept(this.item);
		}
	}

	/**
	 * Synchronised access to the enclosed item with return value.
	 */
	public <E extends Exception, T> T applyItem (ExFunction<Item, T, E> fn) throws E {
		synchronized (this.item) {
			return fn.apply(this.item);
		}
	}

	public <E extends Exception> void withEachChildContainer (ExConsumer<Container, E> consumer) throws E {
		synchronized (this.container) {
			for (final Container child : this.container.getContainers()) {
				synchronized (child) {
					consumer.accept(child);
				}
			}
		}
	}

	public <E extends Exception> void withEachChildItem (ExConsumer<Item, E> consumer) throws E {
		synchronized (this.container) {
			for (final Item item : this.container.getItems()) {
				synchronized (item) {
					consumer.accept(item);
				}
			}
		}
	}

	public File getFile () {
		return this.file;
	}

	public MediaFormat getFormat() {
		return this.format;
	}

	public boolean hasItem () {
		return this.item != null;
	}

	public boolean isItem () {
		return this.isItem;
	}

	public boolean hasExistantFile () {
		return this.file != null && this.file.exists();
	}

	public String getTitle() {
		if (this.item != null) {
			synchronized (this.item) {
				return this.item.getTitle();
			}
		}
		if (this.container != null) {
			synchronized (this.container) {
				return this.container.getTitle();
			}
		}
		return null;
	}

	public long getFileLength() {
		return this.fileLength;
	}

	public long getLastModified() {
		return this.lastModified;
	}

	public String getParentId () {
		if (this.container != null) {
			synchronized (this.container) {
				return this.container.getParentID();
			}
		}
		else if (this.item != null) {
			synchronized (this.item) {
				return this.item.getParentID();
			}
		}
		else {
			throw new IllegalStateException("No parent.");
		}
	}

	/**
	 * The string to use when sorting, put in the "Creator" field for want of a better place.
	 */
	public void setSortName(String val) {
		synchronized (this.container) {
			this.container.setCreator(val);
		}
	}

	public void addContainerProperty(Property<?> property) {
		synchronized (this.container) {
			this.container.addProperty(property);
		}

	}

	public int getChildCount() {
		if (this.container == null) return 0;

		final Integer childCount;
		synchronized (this.container) {
			childCount = this.container.getChildCount();
		}
		if (childCount == null) return 0;
		return childCount.intValue();
	}

	public int getChildContainerCount() {
		if (this.container == null) return 0;
		synchronized (this.container) {
			return this.container.getContainers().size();
		}
	}

	public int getChildItemCount() {
		if (this.container == null) return 0;
		synchronized (this.container) {
			return this.container.getItems().size();
		}
	}

	public boolean addChild(final ContentNode child) {
		if (child.isItem()) {
			return addChildItemIfAbsent(child.item);
		}
		else if (child.hasContainer()) {
			return addChildContainerIfAbsent(child.container);
		}
		else {
			throw new IllegalStateException();
		}
	}

	public boolean addChildContainerIfAbsent(final Container childContainer) {
		synchronized (this.container) {
			if (hasChildContainerWithId(childContainer.getId())) return false;

			this.container.addContainer(childContainer);
			Collections.sort(this.container.getContainers(), DIDLObjectOrder.CREATOR);
			updateContainerSize();
			return true;
		}
	}

	public boolean addChildItemIfAbsent(final Item childItem) {
		synchronized (this.container) {
			if (hasChildItemWithId(childItem.getId())) return false;

			this.container.addItem(childItem);
			updateContainerSize();
			return true;
		}
	}

	public boolean removeChild (final ContentNode toRemove) {
		return removeChildById(toRemove.getId());
	}

	public boolean removeChild (final DIDLObject toRemove) {
		return removeChildById(toRemove.getId());
	}

	public boolean removeChildById (final String id) {
		synchronized (this.container) {
			boolean removed = removeById(this.container.getContainers(), id);
			if (!removed) removed = removeById(this.container.getItems(), id);
			if (removed) updateContainerSize();
			return removed;
		}
	}

	private static <T extends DIDLObject> boolean removeById(List<T> list, final String id) {
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

	/**
	 * Return true to keep, false to remove.
	 */
	public void maybeRemoveChildItems(Function<Item, Boolean> f) {
		synchronized (this.container) {
			final Iterator<Item> it = this.container.getItems().iterator();
			boolean removed = false;
			while (it.hasNext()) {
				final Item item = it.next();
				if (!f.apply(item)) {
					it.remove();
					removed = true;
				}
			}
			if (removed) updateContainerSize();
		}
	}

	// Assumes already synchronised.
	private void updateContainerSize() {
		if (this.container == null) return;
		this.container.setChildCount(this.container.getContainers().size() + this.container.getItems().size());
	}

	public boolean hasChildContainerWithId(String childId) {
		synchronized (this.container) {
			for (final Container c : this.container.getContainers()) {
				if (childId.equals(c.getId())) return true;
			}
		}
		return false;
	}

	public boolean hasChildItemWithId(String childId) {
		synchronized (this.container) {
			for (final Item i : this.container.getItems()) {
				if (childId.equals(i.getId())) return true;
			}
		}
		return false;
	}

	public void reload() {
		if (this.file == null || !this.file.exists()) return;
		this.fileLength = this.file.length();
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
		if (this.isItem) {
			s.append("item{");
		}
		else {
			s.append("contaner{");
		}
		s.append("id=").append(this.id);
		if (this.isItem) {
			s.append(" file=").append(this.file);
		}

		if (this.container != null) {
			synchronized (this.container) {
				s.append(" containers=");
				appendToString(s, this.container.getContainers());
				s.append(" items=");
				appendToString(s, this.container.getItems());
			}
		}

		return s.append("}").toString();
	}

	private void appendToString(final StringBuilder s, final List<? extends DIDLObject> list) {
		if (list == null) {
			s.append("null");
			return;
		}

		s.append("[");
		boolean first = true;
		for (final DIDLObject o : list) {
			if (!first) s.append(", ");
			s.append(o.getTitle());
			first = false;
		}
		s.append("]");
	}

	public enum Order implements Comparator<ContentNode> {
		MODIFIED_DESC {
			@Override
			public int compare (final ContentNode a, final ContentNode b) {
				int c = Long.compare(b.lastModified, a.lastModified);
				if (c == 0) {
					c = TITLE.compare(a, b);
				}
				return c;
			}
		},
		TITLE {
			@Override
			public int compare(final ContentNode a, final ContentNode b) {
				final String at = a.getTitle();
				final String bt = b.getTitle();
				int c = (at != null ? (bt != null ? at.compareTo(bt) : 1) : -1);
				if (c == 0) {
					c = ID.compare(a, b);
				}
				return c;
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

package com.vaguehope.dlnatoad.dlnaserver;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.media.MediaFormat;

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

	private volatile long lastModified = 0L;

	public ContentNode (final String id, final Container container) {
		if (id == null) throw new IllegalArgumentException("id must not be null.");
		if (container == null) throw new IllegalArgumentException("container must not be null.");
		if (container.getId() == null)  throw new IllegalArgumentException("container must not have null id.");
		this.id = id;
		this.item = null;
		this.container = container;
		this.file = null;
		this.format = null;
		this.isItem = false;
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

	public Container getContainer () {
		return this.container;
	}

	public Item getItem () {
		return this.item;
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

	public String getTitle() {
		if (this.item != null) {
			return this.item.getTitle();
		}
		if (this.container != null) {
			return this.container.getTitle();
		}
		return null;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void reload() {
		if (file == null) return;
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

		s.append(" containers=");
		if (this.container != null) appendToString(s, this.container.getContainers());
		s.append(" items=");
		if (this.container != null) appendToString(s, this.container.getItems());

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

package com.vaguehope.dlnatoad.dlnaserver;

import java.io.File;
import java.util.List;

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

	public ContentNode (final String id, final Container container) {
		this.id = id;
		this.item = null;
		this.container = container;
		this.file = null;
		this.format = null;
		this.isItem = false;
	}

	public ContentNode (final String id, final Item item, final File file, final MediaFormat format) {
		this.id = id;
		this.item = item;
		this.container = null;
		this.file = file;
		this.format = format;
		this.isItem = true;
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

	public boolean isItem () {
		return this.isItem;
	}

	@Override
	public String toString () {
		StringBuilder s = new StringBuilder();
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
		appendToString(s, this.container.getContainers());
		s.append(" items=");
		appendToString(s, this.container.getItems());

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

}

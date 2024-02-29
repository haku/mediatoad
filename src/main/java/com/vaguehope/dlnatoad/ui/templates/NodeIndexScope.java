package com.vaguehope.dlnatoad.ui.templates;

import java.util.ArrayList;
import java.util.List;

public class NodeIndexScope {

	public final String list_title;
	public final String node_id;
	public final String node_parent_id;
	public boolean show_list_link_row;
	public final String node_file_name;
	public final String node_total_size;
	public final List<IndexItem> list_items = new ArrayList<>();
	public final List<Thumb> thumbs = new ArrayList<>();
	public final List<TopTag> tags = new ArrayList<>();
	public String debugfooter;

	public NodeIndexScope(
			final String list_title,
			final String node_id,
			final String node_parent_id,
			final boolean show_list_link_row,
			final String node_file_name,
			final String node_total_size) {
		this.list_title = list_title;
		this.node_id = node_id;
		this.node_parent_id = node_parent_id;
		this.show_list_link_row = show_list_link_row;
		this.node_file_name = node_file_name;
		this.node_total_size = node_total_size;
	}

	public void addItem(final String path, final String title) {
		addItem(path, title, null, null);
	}

	public void addItem(final String path, final String title, final String size, final String duration) {
		this.list_items.add(new IndexItem(path, title, shouldSetAutofucus(), size, duration));
	}

	public void addThumb(final String item_path, final String thumb_path, final String title) {
		this.thumbs.add(new Thumb(item_path, thumb_path, title, shouldSetAutofucus()));
	}

	public void addTopTag(final String path, final String tag, final int count) {
		this.tags.add(new TopTag(path, tag, count));
	}

	public void setDebugfooter(final String debugfooter) {
		this.debugfooter = debugfooter;
	}

	private boolean shouldSetAutofucus() {
		return this.list_items.size() == 0 && this.thumbs.size() == 0;
	}

	public static class IndexItem {
		public final String path;
		public final String title;
		public final boolean autofocus;
		public final String size;
		public final String duration;

		IndexItem(final String path, final String title, final boolean autofocus, final String size, final String duration) {
			this.path = path;
			this.title = title;
			this.autofocus = autofocus;
			this.size = size;
			this.duration = duration;
		}
	}

	public static class Thumb {
		public final String item_path;
		public final String thumb_path;
		public final String title;
		public final boolean autofocus;

		Thumb(final String item_path, final String thumb_path, final String title, final boolean autofocus) {
			this.item_path = item_path;
			this.thumb_path = thumb_path;
			this.title = title;
			this.autofocus = autofocus;
		}
	}

	public static class TopTag {
		public final String path;
		public final String tag;
		public final int count;

		public TopTag(final String path, final String tag, final int count) {
			this.path = path;
			this.tag = tag;
			this.count = count;
		}
	}

}

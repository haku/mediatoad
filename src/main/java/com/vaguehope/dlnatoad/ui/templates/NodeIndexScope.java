package com.vaguehope.dlnatoad.ui.templates;

import java.util.ArrayList;
import java.util.List;

public class NodeIndexScope {

	public final String list_title;
	public final List<IndexItem> list_items = new ArrayList<>();

	public NodeIndexScope(final String list_title) {
		this.list_title = list_title;
	}

	public void addItem(final String path, final String title) {
		this.list_items.add(new IndexItem(path, title));
	}

	public static class IndexItem {
		public final String path;
		public final String title;

		private IndexItem(final String path, final String title) {
			this.path = path;
			this.title = title;
		}

	}

}

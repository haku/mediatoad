package com.vaguehope.dlnatoad.ui.templates;

import java.util.ArrayList;
import java.util.List;

import com.vaguehope.dlnatoad.ui.templates.NodeIndexScope.IndexItem;
import com.vaguehope.dlnatoad.ui.templates.NodeIndexScope.Thumb;

public class ResultGroupScope {

	public final String group_title;
	public final String msg;
	public final String next_page_path;
	public final List<IndexItem> list_items = new ArrayList<>();
	public final List<Thumb> thumbs = new ArrayList<>();

	private final String pathPrefix;

	ResultGroupScope(final String group_title, final String msg, final String next_page_path, PageScope pageScope) {
		this.group_title = group_title;
		this.msg = msg;
		this.next_page_path = next_page_path;
		this.pathPrefix = pageScope.path_prefix;
	}

	public void addLocalItem(final String path, final String title, final String size, final String duration) {
		addRemoteItem(prefixPath(path), title, size, duration);
	}

	public void addLocalThumb(final String item_path, final String thumb_path, final String title) {
		this.thumbs.add(new Thumb(prefixPath(item_path), prefixPath(thumb_path), title, shouldSetAutofucus()));
	}

	public void addRemoteItem(final String path, final String title, final String size, final String duration) {
		this.list_items.add(new IndexItem(path, title, shouldSetAutofucus(), size, duration));
	}

	private String prefixPath(final String path) {
		if (this.pathPrefix == null || this.pathPrefix.length() < 1) return path;
		return this.pathPrefix + path;
	}

	private boolean shouldSetAutofucus() {
		return this.list_items.size() == 0 && this.thumbs.size() == 0;
	}

}

package com.vaguehope.dlnatoad.ui.templates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.model.ModelUtil;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class ResultGroupScope {

	public final String group_title;
	public final String msg;
	public final String next_page_path;
	public final List<IndexItem> list_items = new ArrayList<>();
	public final List<Thumb> thumbs = new ArrayList<>();

	private final String pathPrefix;

	public ResultGroupScope(final String group_title, final String msg, final String next_page_path, PageScope pageScope) {
		this.group_title = group_title;
		this.msg = msg;
		this.next_page_path = next_page_path;
		this.pathPrefix = pageScope.path_prefix;
	}

	public void addLocalItem(final String path, final String title) {
		addRemoteItem(prefixPath(path), title, null, null);
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

	public void addContentItem(
			final ContentItem i,
			final String linkQuery,
			final ImageResizer imageResizer) throws IOException {

		if (imageResizer != null && i.getFormat().getContentGroup() == ContentGroup.IMAGE) {
			addLocalThumb(
					C.ITEM_PATH_PREFIX + i.getId() + linkQuery,
					C.THUMBS_PATH_PREFIX + i.getId(),
					i.getTitle());
		}
		else {
			final long fileLength = i.getFileLength();
			final long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(i.getDurationMillis());
			addLocalItem(
					C.CONTENT_PATH_PREFIX + i.getId() + "." + i.getFormat().getExt(),
					i.getFile().getName(),
					fileLength > 0 ? FileHelper.readableFileSize(fileLength) : null,
					durationSeconds > 0 ? ModelUtil.toTimeString(durationSeconds) : null);
		}
	}

	private String prefixPath(final String path) {
		if (this.pathPrefix == null || this.pathPrefix.length() < 1) return path;
		return this.pathPrefix + path;
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

}

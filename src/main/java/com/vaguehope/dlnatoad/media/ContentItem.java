package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.vaguehope.dlnatoad.media.MetadataReader.Metadata;
import com.vaguehope.dlnatoad.util.ExConsumer;

public class ContentItem extends AbstractContent {

	private final File file;
	private final MediaFormat format;

	private final List<ContentItem> attachments = new ArrayList<>();

	private volatile long durationMillis;
	private volatile ContentItem art;
	private volatile Metadata metadata;
	private volatile long fileLength = 0L;
	private volatile long lastModified = 0L;

	public ContentItem(
			final String id,
			final String parentId,
			final String title,
			final File file,
			final MediaFormat format) {
		super(id, parentId, title);
		if (parentId == null) throw new IllegalArgumentException("parentId must not be null.");
		this.file = file;
		this.format = format;
		reload();
	}

	public void reload() {
		if (this.file == null || !this.file.exists()) return;
		this.fileLength = this.file.length();
		this.lastModified = this.file.lastModified();
	}

	public void setDurationMillis(final long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public void setArt(final ContentItem art) {
		this.art = art;
	}

	public void setMetadata(final Metadata metadata) {
		this.metadata = metadata;
	}

	/**
	 * return true if added.
	 */
	public boolean addAttachmentIfNotPresent(final ContentItem attachment) {
		if (attachment == null) throw new IllegalArgumentException("attachment must not be null.");
		synchronized (this.attachments) {
			for (final ContentItem a : this.attachments) {
				if (Objects.equals(a.getId(), attachment.getId())) return false;
			}
			this.attachments.add(attachment);
			return true;
		}
	}

	public boolean removeAttachmetById(final String attachmentId) {
		if (attachmentId == null) throw new IllegalArgumentException("attachmentId must not be null.");
		synchronized (this.attachments) {
			final Iterator<ContentItem> ittr = this.attachments.iterator();
			while (ittr.hasNext()) {
				if (attachmentId.equals(ittr.next().getId())) {
					ittr.remove();
					return true;
				}
			}
			return false;
		}
	}

	public boolean hasAttachments() {
		synchronized (this.attachments) {
			return this.attachments.size() > 0;
		}
	}

	public <E extends Exception> void withEachAttachment (final ExConsumer<ContentItem, E> consumer) throws E {
		synchronized (this.attachments) {
			if (this.attachments.size() < 1) return;
			for (final ContentItem item : this.attachments) {
				consumer.accept(item);
			}
		}
	}

	public List<ContentItem> getCopyOfAttachments() {
		return new ArrayList<>(this.attachments);
	}

	public boolean hasExistantFile () {
		return this.file != null && this.file.exists();
	}

	public File getFile () {
		return this.file;
	}

	public MediaFormat getFormat() {
		return this.format;
	}

	public long getDurationMillis() {
		return this.durationMillis;
	}

	public ContentItem getArt() {
		return this.art;
	}

	public Metadata getMetadata() {
		return this.metadata;
	}

	public long getFileLength() {
		return this.fileLength;
	}

	public long getLastModified() {
		return this.lastModified;
	}

	@Override
	public String toString() {
		return String.format("ContentItem{%s, %s, %s, %s, %s}", this.id, this.parentId, this.title, this.file.getAbsolutePath(), this.format);
	}

	@Override
	public int hashCode() {
		return this.id.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == this) return true;
		if (obj == null) return false;
		if (!(obj instanceof ContentItem)) return false;
		final ContentItem that = (ContentItem) obj;
		return Objects.equals(this.id, that.id);
	}

	public enum Order implements Comparator<ContentItem> {
		MODIFIED_DESC {
			@Override
			public int compare (final ContentItem a, final ContentItem b) {
				int c = Long.compare(b.lastModified, a.lastModified);
				if (c == 0) {
					c = TITLE.compare(a, b);
				}
				return c;
			}
		},
		TITLE {
			@Override
			public int compare(final ContentItem a, final ContentItem b) {
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
			public int compare(final ContentItem a, final ContentItem b) {
				return a.id.compareTo(b.id);
			}
		};

		@Override
		public abstract int compare (ContentItem a, ContentItem b);
	}

}

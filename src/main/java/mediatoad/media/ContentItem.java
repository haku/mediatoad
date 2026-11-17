package mediatoad.media;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.google.common.annotations.VisibleForTesting;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mediatoad.media.MetadataReader.Metadata;
import mediatoad.util.ExConsumer;

public class ContentItem extends AbstractContent {

	private final File file;
	private final MediaFormat format;

	private final List<ContentItem> attachments = new ArrayList<>();

	private volatile long durationMillis;
	private volatile int width;
	private volatile int height;
	private volatile ContentItem art;
	private volatile Metadata metadata;
	private volatile long fileLength = 0L;
	private volatile long createdMillis = 0L;
	private volatile long modifiedMillis = 0L;

	public ContentItem(
			final String id,
			final String parentId,
			final String title,
			final File file,
			final MediaFormat format) throws IOException {
		super(id, parentId, title);
		if (parentId == null) throw new IllegalArgumentException("parentId must not be null.");
		this.file = file;
		this.format = format;
		reload();
	}

	public void reload() throws IOException {
		if (this.file == null || !this.file.exists()) return;

		final Path path = this.file.toPath();
		if (path == null) return;  // For when File is a mock object.

		final BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
		this.fileLength = attrs.size();
		this.modifiedMillis = attrs.lastModifiedTime().toMillis();
		this.createdMillis = attrs.creationTime().toMillis();
	}

	@VisibleForTesting
	public void setFileLength(final long fileLength) {
		this.fileLength = fileLength;
	}

	@VisibleForTesting
	public void setCreatedMillis(final long createdMillis) {
		this.createdMillis = createdMillis;
	}

	@VisibleForTesting
	public void setModifiedMillis(final long modifiedMillis) {
		this.modifiedMillis = modifiedMillis;
	}

	public void setDurationMillis(final long durationMillis) {
		this.durationMillis = durationMillis;
	}

	@SuppressFBWarnings(value = "EI_EXPOSE_REP2")
	public void setArt(final ContentItem art) {
		this.art = art;
	}

	public void setMetadata(final Metadata metadata) {
		this.metadata = metadata;
	}

	public void setWidthAndHeight(final int width, final int height) {
		this.width = width;
		this.height = height;
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

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	@SuppressFBWarnings(value = "EI_EXPOSE_REP")
	public ContentItem getArt() {
		return this.art;
	}

	public Metadata getMetadata() {
		return this.metadata;
	}

	public long getFileLength() {
		return this.fileLength;
	}

	public long getModifiedMillis() {
		return this.modifiedMillis;
	}

	public long getCreatedMillis() {
		return this.createdMillis;
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
				int c = Long.compare(b.modifiedMillis, a.modifiedMillis);
				if (c == 0) {
					c = TITLE_CASE_INSENSITIVE.compare(a, b);
				}
				return c;
			}
		},
		TITLE_CASE_INSENSITIVE {
			@Override
			public int compare(final ContentItem a, final ContentItem b) {
				final String at = a.getTitle();
				final String bt = b.getTitle();
				int c = (at != null ? (bt != null ? at.compareToIgnoreCase(bt) : 1) : -1);
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

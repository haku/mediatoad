package com.vaguehope.dlnatoad.db;

import java.util.List;
import java.util.Objects;

public class FileIdAndTags {

	private final String fileId;
	private final List<Tag> tags;

	public FileIdAndTags(String fileId, List<Tag> tags) {
		this.fileId = fileId;
		this.tags = tags;
	}

	@Override
	public String toString() {
		return String.format("FileIdAndTags{%s, %s}", this.fileId, this.tags);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.fileId, this.tags);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof FileIdAndTags)) return false;
		final FileIdAndTags that = (FileIdAndTags) obj;
		return Objects.equals(this.fileId, that.fileId)
				&& Objects.equals(this.tags, that.tags);
	}

}

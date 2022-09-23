package com.vaguehope.dlnatoad.importer;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

class HashAndTags {

	private final BigInteger sha1;
	private final BigInteger md5;
	private final List<ImportedTag> tags;

	public static HashAndTags sha1(final BigInteger sha1, final List<ImportedTag> tags) {
		return new HashAndTags(sha1, null, tags);
	}

	public static HashAndTags md5(final BigInteger md5, final List<ImportedTag> tags) {
		return new HashAndTags(null, md5, tags);
	}

	private HashAndTags(final BigInteger sha1, final BigInteger md5, final List<ImportedTag> tags) {
		this.sha1 = sha1;
		this.md5 = md5;
		this.tags = tags;
	}

	public BigInteger getSha1() {
		return this.sha1;
	}

	public BigInteger getMd5() {
		return this.md5;
	}

	public Collection<ImportedTag> getTags() {
		return this.tags;
	}

	@Override
	public String toString() {
		return String.format("HashAndTags{%s, %s, %s}", this.sha1, this.md5, this.tags);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.sha1, this.md5, this.tags);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof HashAndTags)) return false;
		final HashAndTags that = (HashAndTags) obj;
		return Objects.equals(this.sha1, that.sha1)
				&& Objects.equals(this.md5, that.md5)
				&& Objects.equals(this.tags, that.tags);
	}

	public static class ImportedTag {
		private final String tag;
		private final String cls;
		private final long mod;
		private final boolean del;

		public ImportedTag(final String tag, final String cls, final long mod, final boolean del) {
			this.tag = tag;
			this.cls = cls;
			this.mod = mod;
			this.del = del;
		}

		public String getTag() {
			return this.tag;
		}

		public String getCls() {
			return this.cls;
		}

		public long getMod() {
			return this.mod;
		}

		public boolean isDel() {
			return this.del;
		}

		@Override
		public String toString() {
			return String.format("Tag{%s, %s, %s, %s}", this.tag, this.cls, this.mod, this.del);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.tag, this.cls, this.mod, this.del);
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == null) return false;
			if (this == obj) return true;
			if (!(obj instanceof ImportedTag)) return false;
			final ImportedTag that = (ImportedTag) obj;
			return Objects.equals(this.tag, that.tag)
					&& Objects.equals(this.cls, that.cls)
					&& Objects.equals(this.mod, that.mod)
					&& Objects.equals(this.del, that.del);
		}
	}

}
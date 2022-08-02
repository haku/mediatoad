package com.vaguehope.dlnatoad.importer;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Objects;


class HashAndTags {

	private final BigInteger sha1;
	private final List<String> tags;

	public HashAndTags(final BigInteger sha1, final List<String> tags) {
		this.sha1 = sha1;
		this.tags = tags;
	}

	public BigInteger getSha1() {
		return this.sha1;
	}

	public Collection<String> getTags() {
		return this.tags;
	}

	@Override
	public String toString() {
		return String.format("HashAndTags{%s, %s}", this.sha1, this.tags);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.sha1, this.tags);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof HashAndTags)) return false;
		final HashAndTags that = (HashAndTags) obj;
		return Objects.equals(this.sha1, that.sha1)
				&& Objects.equals(this.tags, that.tags);
	}

}
package com.vaguehope.dlnatoad.tagdeterminer;

import java.util.Objects;

import com.vaguehope.dlnatoad.rpc.RpcTarget;

public class TagDeterminer {

	private final RpcTarget target;
	private final String query;

	public TagDeterminer(final RpcTarget target, final String query) {
		this.target = target;
		this.query = query;
	}

	public RpcTarget getTarget() {
		return this.target;
	}

	public String getQuery() {
		return this.query;
	}

	@Override
	public String toString() {
		return String.format("TagDeterminer{%s, %s}", this.target, this.query);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.target, this.query);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof TagDeterminer)) return false;
		final TagDeterminer that = (TagDeterminer) obj;
		return Objects.equals(this.target, that.target)
				&& Objects.equals(this.query, that.query);
	}

}

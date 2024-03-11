package com.vaguehope.dlnatoad.rpc.client;

import java.util.Objects;

public class RemoteInstance {

	private final String id;
	private final String target;
	private final boolean plainText;

	public RemoteInstance(final String id, final String target, final boolean plainText) {
		this.id = id;
		this.target = target;
		this.plainText = plainText;
	}

	public String getId() {
		return this.id;
	}

	public String getTarget() {
		return this.target;
	}

	public boolean isPlainText() {
		return this.plainText;
	}

	@Override
	public String toString() {
		return String.format("RemoteInstance{%s, %s, %s}", this.id, this.target, this.plainText);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.target, this.plainText);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof RemoteInstance)) return false;
		final RemoteInstance that = (RemoteInstance) obj;
		return Objects.equals(this.target, that.target)
				&& Objects.equals(this.id, that.id)
				&& Objects.equals(this.plainText, that.plainText);
	}


}

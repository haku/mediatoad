package com.vaguehope.dlnatoad.rpc.client;

import java.util.Objects;

import com.vaguehope.dlnatoad.rpc.RpcTarget;

public class RemoteInstance {

	private final String id;
	private final RpcTarget target;

	public RemoteInstance(final String id, final RpcTarget target) {
		this.id = id;
		this.target = target;
	}

	public String getId() {
		return this.id;
	}

	public RpcTarget getTarget() {
		return this.target;
	}

	@Override
	public String toString() {
		return String.format("RemoteInstance{%s, %s, %s}", this.id, this.target);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.target);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof RemoteInstance)) return false;
		final RemoteInstance that = (RemoteInstance) obj;
		return Objects.equals(this.target, that.target)
				&& Objects.equals(this.id, that.id);
	}


}

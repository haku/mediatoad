package com.vaguehope.dlnatoad.db;

import com.google.common.base.Objects;

public class Playback {

	private final long dateLastPlayed;
	private final int startCount;
	private final int completeCount;
	private final boolean excluded;

	public Playback(final long dateLastPlayed, final int startCount, final int completeCount, final boolean excluded) {
		this.dateLastPlayed = dateLastPlayed;
		this.startCount = startCount;
		this.completeCount = completeCount;
		this.excluded = excluded;
	}

	public long getDateLastPlayed() {
		return this.dateLastPlayed;
	}

	public int getStartCount() {
		return this.startCount;
	}

	public int getCompleteCount() {
		return this.completeCount;
	}

	public boolean isExcluded() {
		return this.excluded;
	}

	@Override
	public boolean equals(final Object aThat) {
		if (aThat == null) return false;
		if (this == aThat) return true;
		if (!(aThat instanceof Playback)) return false;
		final Playback that = (Playback) aThat;

		return Objects.equal(this.dateLastPlayed, that.dateLastPlayed)
				&& Objects.equal(this.startCount, that.startCount)
				&& Objects.equal(this.completeCount, that.completeCount)
				&& Objects.equal(this.excluded, that.excluded);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.dateLastPlayed, this.startCount, this.completeCount, this.excluded);
	}

}

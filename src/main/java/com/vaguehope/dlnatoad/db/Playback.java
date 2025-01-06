package com.vaguehope.dlnatoad.db;

import com.google.common.base.Objects;

public class Playback {

	private final long dateLastPlayed;
	private final int startCount;
	private final int completeCount;

	public Playback(long dateLastPlayed, int startCount, int completeCount) {
		this.dateLastPlayed = dateLastPlayed;
		this.startCount = startCount;
		this.completeCount = completeCount;
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

	@Override
	public boolean equals(final Object aThat) {
		if (aThat == null) return false;
		if (this == aThat) return true;
		if (!(aThat instanceof Playback)) return false;
		final Playback that = (Playback) aThat;

		return Objects.equal(this.dateLastPlayed, that.dateLastPlayed)
				&& Objects.equal(this.startCount, that.startCount)
				&& Objects.equal(this.completeCount, that.completeCount);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.dateLastPlayed, this.startCount, this.completeCount);
	}

}

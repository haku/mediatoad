package com.vaguehope.dlnatoad;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Ticker;

public class FakeTicker extends Ticker {

	private volatile long time;

	public FakeTicker() {
		this.time = System.nanoTime();
	}

	public void addTime(final long duration, final TimeUnit unit) {
		this.time += unit.toNanos(duration);
	}

	@Override
	public long read() {
		return this.time;
	}

}

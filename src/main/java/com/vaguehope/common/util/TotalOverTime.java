package com.vaguehope.common.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import com.google.common.base.Ticker;

public class TotalOverTime {

	private final long totalDurationNanos;
	private final long bucketDurationNanos;
	private final Ticker ticker;

	private final AtomicLong total;
	private final AtomicLongArray ringBuffer;
	private volatile long lastIncrementNanos;

	public TotalOverTime(final long duration, final TimeUnit timeUnit, final int bucketCount) {
		this(duration, timeUnit, bucketCount, Ticker.systemTicker());
	}

	protected TotalOverTime(final long duration, final TimeUnit timeUnit, final int bucketCount, final Ticker ticker) {
		this.totalDurationNanos = timeUnit.toNanos(duration);
		this.bucketDurationNanos = this.totalDurationNanos / bucketCount;
		this.ticker = ticker;
		this.total = new AtomicLong();
		this.ringBuffer = new AtomicLongArray(bucketCount);
		this.lastIncrementNanos = ticker.read();
	}

	public void increment(final long delta) {
		final long now = this.ticker.read();

		final long lastIncrement = this.lastIncrementNanos;
		this.lastIncrementNanos = now;
		final int expiredBucketCount = (int) ((now - lastIncrement) / this.bucketDurationNanos);
		if (expiredBucketCount > 0) {
			final int lastIncrementedBucket = (int) ((lastIncrement % this.totalDurationNanos) / this.bucketDurationNanos);
			for (int x = 1; x <= expiredBucketCount; x++) {
				int i = lastIncrementedBucket + x;
				if (i > this.ringBuffer.length() - 1) i -= this.ringBuffer.length();
				final long removed = this.ringBuffer.getAndSet(i, 0);
				this.total.addAndGet(-removed);
			}
		}

		final int currentBucket = (int) ((now % this.totalDurationNanos) / this.bucketDurationNanos);
		this.ringBuffer.addAndGet(currentBucket, delta);
		this.total.addAndGet(delta);
	}

	public long get() {
		return this.total.get();
	}

}

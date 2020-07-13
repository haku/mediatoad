package com.vaguehope.dlnatoad.util;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public interface Time {

	long now();

	public static final Time DEFAULT = new DefaultTime();

	class DefaultTime implements Time {

		private static final long NANO_ORIGIN = System.nanoTime();

		@Override
		public long now() {
			return System.nanoTime() - NANO_ORIGIN;
		}
	}

	public class FakeTime implements Time {

		private final AtomicLong time = new AtomicLong(new Random().nextLong());

		@Override
		public long now() {
			return this.time.get();
		}

		public void advance(final long amount, final TimeUnit unit) {
			this.time.addAndGet(unit.toNanos(amount));
		}

	}

}

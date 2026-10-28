package mediatoad.util;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

	@SuppressFBWarnings(value = "PREDICTABLE_RANDOM")
	public class FakeTime implements Time {

		private static final Random RANDOM = new Random();

		private final AtomicLong time = new AtomicLong(1000000000000L + RANDOM.nextInt());

		@Override
		public long now() {
			return this.time.get();
		}

		public void advance(final long amount, final TimeUnit unit) {
			this.time.addAndGet(unit.toNanos(amount));
		}

	}

}

package com.vaguehope.dlnatoad.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DaemonThreadFactory implements ThreadFactory {

	private final String prefix;
	private final LoggingThreadGroup threadGroup;
	private final AtomicInteger counter = new AtomicInteger(0);

	public DaemonThreadFactory (final String prefix) {
		this.prefix = prefix;
		this.threadGroup = new LoggingThreadGroup(Thread.currentThread().getThreadGroup(), prefix);
	}

	@Override
	public Thread newThread (final Runnable r) {
		final Thread t = new Thread(this.threadGroup, r,
				"t-" + this.prefix + this.counter.getAndIncrement(),
				0);
		if (!t.isDaemon()) t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	}

	private static class LoggingThreadGroup extends ThreadGroup {

		public LoggingThreadGroup (final ThreadGroup parent, final String prefix) {
			super(parent, "tg-" + prefix);
		}

		@Override
		public void uncaughtException (final Thread t, final Throwable e) {
			e.printStackTrace(System.err);
		}

	}

}

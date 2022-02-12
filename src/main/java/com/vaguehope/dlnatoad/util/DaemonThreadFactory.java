package com.vaguehope.dlnatoad.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaemonThreadFactory implements ThreadFactory {

	private static final Logger LOG = LoggerFactory.getLogger(DaemonThreadFactory.class);

	private final String prefix;
	private final LoggingThreadGroup threadGroup;
	private final AtomicInteger counter = new AtomicInteger(0);
	private final int priority;

	public DaemonThreadFactory (final String prefix) {
		this(prefix, Thread.NORM_PRIORITY - 1);
	}

	public DaemonThreadFactory (final String prefix, final int priority) {
		this.prefix = prefix;
		this.priority = priority;
		this.threadGroup = new LoggingThreadGroup(Thread.currentThread().getThreadGroup(), prefix);
		this.threadGroup.setMaxPriority(priority);
		LOG.debug("Thread group '{}' has priority: {}", prefix, priority);
	}

	@Override
	public Thread newThread (final Runnable r) {
		final Thread t = new Thread(this.threadGroup, r,
				"t-" + this.prefix + this.counter.getAndIncrement(),
				0);
		if (!t.isDaemon()) t.setDaemon(true);
		t.setPriority(this.priority);
		return t;
	}

	private static class LoggingThreadGroup extends ThreadGroup {

		public LoggingThreadGroup (final ThreadGroup parent, final String prefix) {
			super(parent, "tg-" + prefix);
		}

		@Override
		public void uncaughtException (final Thread t, final Throwable e) {
			e.printStackTrace(System.err);
			System.err.println("That was an uncaught exception in: " + t);
		}

	}

}

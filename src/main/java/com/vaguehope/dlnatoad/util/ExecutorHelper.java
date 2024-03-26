package com.vaguehope.dlnatoad.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorHelper {

	public static ExecutorService newExecutor(final int maxThreads, final String name) {
		return newExecutor(0, maxThreads, name, Thread.MIN_PRIORITY);
	}

	public static ExecutorService newExecutor(final int minThreads, final int maxThreads, final String name, final int priority) {
		final ExecutorService e = new ThreadPoolExecutor(minThreads, maxThreads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
				new DaemonThreadFactory(name, priority));
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				e.shutdown();
			}
		});
		return e;
	}

	public static ScheduledExecutorService newScheduledExecutor(final int threads, final String name) {
		return newScheduledExecutor(threads, name, Thread.MIN_PRIORITY);
	}

	public static ScheduledExecutorService newScheduledExecutor(final int threads, final String name, final int priority) {
		final ScheduledExecutorService e = new ScheduledThreadPoolExecutor(threads, new DaemonThreadFactory(name, priority));
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				e.shutdown();
			}
		});
		return e;
	}

}

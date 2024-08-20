package com.vaguehope.dlnatoad.util;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;

public class ExecutorHelper {

	private static final Map<String, Queue<?>> EXECUTOR_QUEUES = new ConcurrentHashMap<>();

	@SuppressWarnings("unused")
	private static final GaugeWithCallback QUEUE_METRIC = GaugeWithCallback.builder()
			.name("executor_queue_size")
			.labelNames("name")
			.help("number of tasks waiting in an executor's queue (including recurring scheduled tasks).")
			.callback((cb) -> {
				for (final Entry<String, Queue<?>> e : EXECUTOR_QUEUES.entrySet()) {
					cb.call(e.getValue().size(), e.getKey());
				}
			})
			.register();

	public static ExecutorService newExecutor(final int maxThreads, final String name) {
		return newExecutor(0, maxThreads, name, Thread.MIN_PRIORITY);
	}

	public static ExecutorService newExecutor(final int minThreads, final int maxThreads, final String name, final int priority) {
		final ThreadPoolExecutor e = new ThreadPoolExecutor(
				minThreads,
				maxThreads,
				60L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(),
				new DaemonThreadFactory(name, priority)) {

			@Override
			public void shutdown() {
				EXECUTOR_QUEUES.remove(name);
				super.shutdown();
			}

			@Override
			public List<Runnable> shutdownNow() {
				EXECUTOR_QUEUES.remove(name);
				return super.shutdownNow();
			}
		};

		EXECUTOR_QUEUES.put(name, e.getQueue());

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
		final ScheduledThreadPoolExecutor e = new ScheduledThreadPoolExecutor(
				threads,
				new DaemonThreadFactory(name, priority)) {

			@Override
			public void shutdown() {
				EXECUTOR_QUEUES.remove(name);
				super.shutdown();
			}

			@Override
			public List<Runnable> shutdownNow() {
				EXECUTOR_QUEUES.remove(name);
				return super.shutdownNow();
			}

		};

		EXECUTOR_QUEUES.put(name, e.getQueue());

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				e.shutdown();
			}
		});
		return e;
	}

}

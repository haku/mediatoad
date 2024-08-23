package com.vaguehope.dlnatoad;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FakeScheduledExecutorService implements ScheduledExecutorService {

	@Override
	public void execute(final Runnable command) {
		command.run();
	}

	@Override
	public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
		command.run();
		return null;
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
		return null;
	}

	@Override
	public <T> Future<T> submit(final Callable<T> task) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> Future<T> submit(final Runnable task, final T result) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Future<?> submit(final Runnable task) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void shutdown() {
	}

	@Override
	public List<Runnable> shutdownNow() {
		return null;
	}

	@Override
	public boolean isShutdown() {
		return false;
	}

	@Override
	public boolean isTerminated() {
		return false;
	}

	@Override
	public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
		return false;
	}

}

package com.vaguehope.dlnatoad.util;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.util.Watcher.EventResult;
import com.vaguehope.dlnatoad.util.Watcher.EventType;
import com.vaguehope.dlnatoad.util.Watcher.FileListener;

public class WatcherTest {

	private ExecutorService schEx;
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();
	private File tmpRoot;
	private FileListener listener;
	private Watcher undertest;
	private Future<Void> undertestRunFuture;

	@Before
	public void before() throws Exception {
		this.schEx = Executors.newSingleThreadExecutor(new DaemonThreadFactory("test"));
		this.tmpRoot = this.tmp.getRoot();
		final List<File> roots = new ArrayList<File>();
		roots.add(this.tmpRoot);
		this.listener = mock(FileListener.class);
		this.undertest = new Watcher(roots, MediaFormat.MediaFileFilter.INSTANCE, this.listener);
	}

	@After
	public void after() {
		this.undertest.shutdown();
	}

	private void startWatcher(final int waitForEventCount, final int timeoutSeconds) throws Exception {
		final AtomicInteger calls = new AtomicInteger(0);
		final Answer<EventResult> answer = new Answer<EventResult>() {
			@Override
			public EventResult answer(final InvocationOnMock invocation) throws Throwable {
				System.out.println(String.format("Event: %s %s", invocation.getMethod().getName(),
						Arrays.toString(invocation.getArguments())));
				if (calls.incrementAndGet() >= waitForEventCount) {
					WatcherTest.this.undertest.shutdown();
				}
				return null;
			}
		};
		doAnswer(answer).when(this.listener).fileFound(any(File.class), any(File.class), any(EventType.class),
				any(Runnable.class));
		doAnswer(answer).when(this.listener).fileModified(any(File.class), any(File.class), any(Runnable.class));
		doAnswer(answer).when(this.listener).fileGone(any(File.class));
		this.undertestRunFuture = this.schEx.submit(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				WatcherTest.this.undertest.run();
				return null;
			}
		});
		this.undertest.waitForPrescan(timeoutSeconds, TimeUnit.SECONDS);
	}

	private void waitForWatcher(final int timeoutSeconds) throws Exception {
		this.undertestRunFuture.get(timeoutSeconds, TimeUnit.SECONDS);
	}

	@Test
	public void itFindsPreexistingFile() throws Exception {
		final File f1 = this.tmp.newFile("file1.mp4");
		startWatcher(1, 10);
		waitForWatcher(10);
		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.SCAN, null);
	}

	@Ignore // TODO Make this test faster by messing with the clock.
	@Test
	public void itFindsNewFile() throws Exception {
		startWatcher(1, 10);
		final File f1 = this.tmp.newFile("file1.mp4");
		waitForWatcher(60);
		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.NOTIFY, null);
	}

}

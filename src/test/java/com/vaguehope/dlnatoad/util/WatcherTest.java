package com.vaguehope.dlnatoad.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
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
	private Time.FakeTime time;
	private Watcher undertest;
	private Future<Void> undertestRunFuture;

	@Before
	public void before() throws Exception {
		this.schEx = Executors.newSingleThreadExecutor(new DaemonThreadFactory("test"));
		this.tmpRoot = this.tmp.getRoot();
		final List<File> roots = new ArrayList<>();
		roots.add(this.tmpRoot);
		this.listener = mock(FileListener.class);
		this.time = new Time.FakeTime();
		this.undertest = new Watcher(roots, MediaFormat.MediaFileFilter.INSTANCE, this.listener, this.time, 200);
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
				final int invocationNumber = calls.incrementAndGet();
				System.out.println(String.format(
						"Event %s: %s %s",
						invocationNumber,
						invocation.getMethod().getName(),
						Arrays.toString(invocation.getArguments())));
				if (invocationNumber >= waitForEventCount) {
					System.out.println(String.format(
							"All %s of %s expected events received, shutting down watcher.",
							invocationNumber,
							waitForEventCount));
					WatcherTest.this.undertest.shutdown();
				}
				return null;
			}
		};
		doAnswer(answer).when(this.listener).fileFound(any(File.class), any(File.class), any(EventType.class), nullable(Runnable.class));
		doAnswer(answer).when(this.listener).fileModified(any(File.class), any(File.class), nullable(Runnable.class));
		doAnswer(answer).when(this.listener).fileGone(any(File.class), anyBoolean());
		this.undertestRunFuture = this.schEx.submit(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				WatcherTest.this.undertest.run();
				return null;
			}
		});
		this.undertest.waitForPrescan(timeoutSeconds, TimeUnit.SECONDS);

		if (waitForEventCount < 1) {
			this.undertest.shutdown();
		}
	}

	private void waitForWatcher(final int timeoutSeconds) throws Exception {
		this.undertestRunFuture.get(timeoutSeconds, TimeUnit.SECONDS);
	}

	private void waitForTotalWatchEventCount(final int count, final int timeoutSeconds) throws InterruptedException {
		final long startTime = System.nanoTime();
		while (this.undertest.getWatchEventCount() < count) {
			System.out.println(String.format("Waiting for events: %s of %s", this.undertest.getWatchEventCount(), count));
			if (System.nanoTime() - startTime > TimeUnit.SECONDS.toNanos(timeoutSeconds)) {
				fail("Timeout waiting for watch event count.");
			}
			Thread.sleep(200);
		}
	}

	@Test
	public void itRunsPrescanCompleteListener() throws Exception {
		final Runnable l = mock(Runnable.class);
		this.undertest.addPrescanCompleteListener(l);
		startWatcher(0, 1);
		waitForWatcher(1);
		verify(l).run();
	}

	@Test
	public void itFindsPreexistingFile() throws Exception {
		final File f1 = this.tmp.newFile("file1.mp4");
		startWatcher(1, 10);
		waitForWatcher(10);
		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.SCAN, null);
	}

	@Test
	public void itFindsNewFile() throws Exception {
		startWatcher(1, 10);

		final File f1 = this.tmp.newFile("file1.mp4");
		waitForTotalWatchEventCount(1, 10);

		this.time.advance(29, TimeUnit.SECONDS);
		verifyNoInteractions(this.listener);

		this.time.advance(2, TimeUnit.SECONDS);
		waitForWatcher(10);
		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.NOTIFY, null);
	}

	@Test
	public void itFindsNewDirAndThenANewFileViaScan() throws Exception {
		startWatcher(1, 10);

		final File d1 = this.tmp.newFolder("dir1");
		final File f1 = new File(d1, "file1.mp4");
		assertTrue(f1.createNewFile());
		waitForTotalWatchEventCount(1, 10);

		waitForWatcher(10);
		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.SCAN, null);
	}

	@Test
	public void itFindsNewDirAndThenANewFileViaWatcher() throws Exception {
		startWatcher(1, 10);  // Event count excludes dirs.

		final File d1 = this.tmp.newFolder("dir1");
		waitForTotalWatchEventCount(1, 10);  // Event count includes dirs.

		final File f1 = new File(d1, "file1.mp4");
		assertTrue(f1.createNewFile());
		waitForTotalWatchEventCount(2, 10);

		this.time.advance(29, TimeUnit.SECONDS);
		verifyNoInteractions(this.listener);

		this.time.advance(2, TimeUnit.SECONDS);
		waitForWatcher(10);
		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.NOTIFY, null);
	}

	@Test
	public void itDetectsModify() throws Exception {
		final File f1 = this.tmp.newFile("file1.mp4");
		startWatcher(2, 10);  // Overall, wait for 2 file callbacks.
		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.SCAN, null);

		FileUtils.write(f1, "data", "UTF-8");
		waitForTotalWatchEventCount(1, 10);  // Event count does not include existing files.

		this.time.advance(29, TimeUnit.SECONDS);
		verifyNoMoreInteractions(this.listener);

		this.time.advance(2, TimeUnit.SECONDS);
		waitForWatcher(10);
		verify(this.listener).fileModified(this.tmpRoot, f1, null);
	}

	@Test
	public void itDetectsDelete() throws Exception {
		final File f1 = this.tmp.newFile("file1.mp4");
		startWatcher(2, 10);
		if (!f1.delete()) fail("Delete failed.");
		waitForWatcher(10);

		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.SCAN, null);
		verify(this.listener, timeout(10000)).fileGone(f1, false);
	}

	@Test
	public void itHandlesDirBeingMoved() throws Exception {
		final File d1 = this.tmp.newFolder("dir1");
		final File d2 = new File(d1, "dir2");
		if (!d2.mkdir()) fail("Failed ot mkdir: " + d2);
		final File f1 = new File(d2, "file1.mp4");
		FileUtils.touch(f1);

		startWatcher(3, 10);
		verify(this.listener).fileFound(this.tmpRoot, f1, EventType.SCAN, null);

		final File d2NewName = new File(d1, "dir2NewName");
		if (!d2.renameTo(d2NewName)) fail("Rename failed.");

		waitForWatcher(10);
		verify(this.listener, timeout(10000)).fileGone(d2, true);

		File f1NewName = new File(d2NewName, f1.getName());
		verify(this.listener).fileFound(this.tmpRoot, f1NewName, EventType.SCAN, null);

		// Note: no delete event for f1 :(
		verifyNoMoreInteractions(this.listener);
	}

}

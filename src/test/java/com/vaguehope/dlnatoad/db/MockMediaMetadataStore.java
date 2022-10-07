package com.vaguehope.dlnatoad.db;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.media.MediaIdCallback;
import com.vaguehope.dlnatoad.media.StoringMediaIdCallback;
import com.vaguehope.dlnatoad.util.DaemonThreadFactory;
import com.vaguehope.dlnatoad.util.ExConsumer;

public class MockMediaMetadataStore extends MediaMetadataStore {

	private static final long TEST_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
	private static final Logger LOG = LoggerFactory.getLogger(MockMediaMetadataStore.class);

	private final TemporaryFolder tmp;
	private final ScheduledExecutorService exSvc;

	public static MockMediaMetadataStore withRealExSvc(final TemporaryFolder tmp) throws SQLException {
		return new MockMediaMetadataStore(tmp, makeRealExSvc());
	}

	public static MockMediaMetadataStore withMockExSvc(final TemporaryFolder tmp) throws SQLException {
		return new MockMediaMetadataStore(tmp, makeMockExSvc());
	}

	private MockMediaMetadataStore(final TemporaryFolder tmp, final ScheduledExecutorService exSvc) throws SQLException {
		super(new InMemoryMediaDb(), exSvc, false);
		this.tmp = tmp;
		this.exSvc = exSvc;
	}

	private static ScheduledExecutorService makeRealExSvc() {
		return new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("mmms", Thread.MIN_PRIORITY));
	}

	private static ScheduledExecutorService makeMockExSvc() {
		final ScheduledExecutorService schEx = mock(ScheduledExecutorService.class);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer (final InvocationOnMock inv) throws Throwable {
				inv.getArgument(0, Runnable.class).run();
				return null;
			}
		}).when(schEx).execute(any(Runnable.class));
		return schEx;
	}

	public void waitForEmptyQueue() throws InterruptedException {
		if (!(this.exSvc instanceof ScheduledThreadPoolExecutor)) return;
		final ScheduledThreadPoolExecutor stpe = (ScheduledThreadPoolExecutor) this.exSvc;

		final long start = System.nanoTime();
		List<Runnable> tasks = null;
		while (System.nanoTime() - start < TEST_TIMEOUT_NANOS) {
			tasks = new ArrayList<>();
			for (final Runnable r : stpe.getQueue()) {
				if (r instanceof RunnableScheduledFuture && ((RunnableScheduledFuture<?>) r).isPeriodic()) continue;
				tasks.add(r);
			}
			if (tasks.size() == 0 && stpe.getActiveCount() < 1) return;
			Thread.sleep(100);
		}
		fail("After timeout queue has items: " + tasks);
	}

	public String addFileWithTags(final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", BigInteger.ZERO, false, tags);
	}

	public String addMissingFileWithTags(final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", BigInteger.ZERO, true, tags);
	}

	public String addFileWithAuthAndTags(final BigInteger auth, final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", auth, false, tags);
	}

	public String addFileWithName(final String nameFragment) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, ".ext", BigInteger.ZERO, false);
	}

	public String addFileWithName(final String nameFragment, final String nameSuffex) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, nameSuffex, BigInteger.ZERO, false);
	}

	public String addFileWithNameAndTags(final String nameFragment, final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, ".ext", BigInteger.ZERO, false, tags);
	}

	public String addFileWithNameExtAndTags(final String nameFragment, final String nameSuffex, final BigInteger auth, final boolean missing, final String... tags) throws IOException, InterruptedException, SQLException {
		final File mediaFile = File.createTempFile("mock_media_" + nameFragment, nameSuffex, this.tmp.getRoot());
		FileUtils.writeStringToFile(mediaFile, RandomStringUtils.randomPrint(10, 50), StandardCharsets.UTF_8);

		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		idForFile(mediaFile, auth, cb);
		final String fileId = cb.getMediaId();
		waitForEmptyQueue();

		try (final WritableMediaDb w = getMediaDb().getWritable()) {
			for (final String tag : tags) {
				w.addTag(fileId, tag, System.currentTimeMillis());
			}
			w.setFileMissing(mediaFile.getAbsolutePath(), missing);
		}

		return fileId;
	}

	public Batch batch() {
		return new Batch();
	}

	public class Batch implements AutoCloseable {
		private final List<Future<?>> futures = new ArrayList<>();
		private final List<ExConsumer<WritableMediaDb, SQLException>> dbWrites = new ArrayList<>();
		private Batch() {}

		@Override
		public void close() throws Exception {
			final long startTime = System.nanoTime();
			for (final Future<?> future : this.futures) {
				future.get();
			}
			final long endTime = System.nanoTime();
			LOG.info("Mock files IDed in: {}ms", TimeUnit.NANOSECONDS.toMillis(endTime - startTime));
			waitForEmptyQueue();
			try (final WritableMediaDb w = getMediaDb().getWritable()) {
				for (final ExConsumer<WritableMediaDb, SQLException> write : this.dbWrites) {
					write.accept(w);
				}
			}
		}

		public Future<String> addFileWithRandomTags() throws IOException, InterruptedException {
			final int count = RandomUtils.nextInt(3, 10);
			final String[] tags = new String[count];
			for (int i = 0; i < count; i++) {
				tags[i] = RandomStringUtils.randomPrint(20, 50);
			}
			return fileWithTags(tags);
		}

		public Future<String> fileWithTags(final String... tags) throws IOException, InterruptedException {
			return fileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", BigInteger.ZERO, false, tags);
		}

		public Future<String> fileWithNameExtAndTags(final String nameFragment, final String nameSuffex, final BigInteger auth, final boolean missing, final String... tags) throws IOException, InterruptedException {
			final File mediaFile = File.createTempFile("mock_media_" + nameFragment, nameSuffex, MockMediaMetadataStore.this.tmp.getRoot());
			FileUtils.writeStringToFile(mediaFile, RandomStringUtils.randomPrint(10, 50), StandardCharsets.UTF_8);

			final CompletableFuture<String> future = new CompletableFuture<>();
			idForFile(mediaFile, auth, new MediaIdCallback() {
				@Override
				public void onResult(final String mediaId) throws IOException {
					Batch.this.dbWrites.add((w) -> {
						for (final String tag : tags) {
							w.addTag(mediaId, tag, System.currentTimeMillis());
						}
						w.setFileMissing(mediaFile.getAbsolutePath(), missing);
					});
					future.complete(mediaId);
				}

				@Override
				public void onError(final IOException e) {
					future.completeExceptionally(e);
				}
			});

			this.futures.add(future);
			return future;
		}

	}

}

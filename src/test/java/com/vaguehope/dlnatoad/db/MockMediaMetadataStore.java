package com.vaguehope.dlnatoad.db;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.FakeScheduledExecutorService;
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
		return new MockMediaMetadataStore(tmp, new FakeScheduledExecutorService());
	}

	private MockMediaMetadataStore(final TemporaryFolder tmp, final ScheduledExecutorService exSvc) throws SQLException {
		super(new InMemoryMediaDb(), exSvc, exSvc, false);
		this.tmp = tmp;
		this.exSvc = exSvc;
	}

	private static ScheduledExecutorService makeRealExSvc() {
		return new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("mmms", Thread.MIN_PRIORITY));
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
			Thread.sleep(10);
		}
		fail("After timeout queue has items: " + tasks);
	}

	public String addFileWithTags(final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", BigInteger.ZERO, false, randomBytes(), null, tags);
	}

	public String addMissingFileWithTags(final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", BigInteger.ZERO, true, randomBytes(), null, tags);
	}

	public String addFileWithAuthAndTags(final BigInteger auth, final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", auth, false, randomBytes(), null, tags);
	}

	public String addFileWithInfoAndTags(final FileInfo info, final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", BigInteger.ZERO, false, randomBytes(), info, tags);
	}

	public String addFileWithName(final String nameFragment) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, ".ext", BigInteger.ZERO, false, randomBytes(), null);
	}

	public String addFileWithName(final String nameFragment, final String nameSuffex) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, nameSuffex, BigInteger.ZERO, false, randomBytes(), null);
	}

	public String addFileWithNameAndTags(final String nameFragment, final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, ".ext", BigInteger.ZERO, false, randomBytes(), null, tags);
	}

	public String addFileWithNameAndSuffexAndTags(final String nameFragment, final String nameSuffex, final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, nameSuffex, BigInteger.ZERO, false, randomBytes(), null, tags);
	}

	public String addFileWithContent(final byte[] fileContent) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", BigInteger.ZERO, false, fileContent, null);
	}

	public String addFileWithContentAndAuth(final byte[] fileContent, final BigInteger auth) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", auth, false, fileContent, null);
	}

	private String addFileWithNameExtAndTags(
			final String nameFragment,
			final String nameSuffex,
			final BigInteger auth,
			final boolean missing,
			final byte[] fileContent,
			final FileInfo info,
			final String... tags) throws IOException, InterruptedException, SQLException {
		final File mediaFile = File.createTempFile("mock_media_" + nameFragment, nameSuffex, this.tmp.getRoot());
		FileUtils.writeByteArrayToFile(mediaFile, fileContent);

		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		idForFile(mediaFile, auth, cb);
		waitForEmptyQueue();
		final String fileId = cb.getMediaId();

		try (final WritableMediaDb w = getMediaDb().getWritable()) {
			for (final String tag : tags) {
				w.addTag(fileId, tag, System.currentTimeMillis());
			}
			w.setFileMissing(mediaFile.getAbsolutePath(), missing);
			if (info != null) w.storeInfos(Arrays.asList(new FileIdAndInfo(fileId, mediaFile, info)));
		}

		return fileId;
	}

	public static byte[] randomBytes() {
		final ThreadLocalRandom rnd = ThreadLocalRandom.current();
		final byte[] ret = new byte[rnd.nextInt(50, 100)];
		rnd.nextBytes(ret);
		return ret;
	}

	public void addNoiseToDb () throws Exception {
		try (final Batch b = batch()) {
			for (int i = 0; i < 10; i++) {
				b.addFileWithRandomTags();
			}
		}
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
				future.get(60, TimeUnit.SECONDS);
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
			final int count = ThreadLocalRandom.current().nextInt(3, 10);
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
			FileUtils.writeStringToFile(mediaFile, RandomStringUtils.randomPrint(50, 100), StandardCharsets.UTF_8);

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

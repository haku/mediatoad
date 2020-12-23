package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import com.vaguehope.dlnatoad.media.StoringMediaIdCallback;
import com.vaguehope.dlnatoad.util.DaemonThreadFactory;

public class MediaDbTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private Random rnd;
	private File dbFile;
	private ScheduledExecutorService schEx;
	private MediaDb undertest;

	private Runnable durationBatchWriter;

	@Before
	public void before () throws Exception {
		this.rnd = new Random();
		this.dbFile = this.tmp.newFile("id-db.db3");
		this.schEx = spy(new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("fs")));
		this.undertest = new MediaDb(this.dbFile, this.schEx);

		final ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);
		verify(this.schEx).scheduleWithFixedDelay(cap.capture(), anyLong(), anyLong(), any(TimeUnit.class));
		this.durationBatchWriter = cap.getValue();
	}

	private String callUndertest(final File file) throws SQLException, IOException {
		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		this.undertest.idForFile(file, cb);
		return cb.getMediaId();
	}

	@SuppressWarnings("unused")
	@Test
	public void itConnectsToExistingDb () throws Exception {
		new MediaDb(this.dbFile, this.schEx);
	}

	@Test
	public void itReturnsSameIdForSameFile () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		assertEquals(
				callUndertest(f1),
				callUndertest(f1));
	}

	@Test
	public void itReturnsDifferentIdsForDifferentFile () throws Exception {
		assertThat(callUndertest(mockMediaFile("media-1.ext")),
				not(equalTo(callUndertest(mockMediaFile("media-2.ext")))));
	}

	@Test
	public void itReturnsSameIdForIdenticalFiles () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = this.tmp.newFile("media-2.ext");
		FileUtils.copyFile(f1, f2, false);
		assertEquals(
				callUndertest(f1),
				callUndertest(f2));
	}

	@Test
	public void itReturnsSameIdWhenFileContentChanges () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callUndertest(f1);
		fillFile(f1);
		assertEquals(id1, callUndertest(f1));
	}

	@Test
	public void itChangesToSameIdWhenFileBecomesSameAsAnother () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = callUndertest(f1);
		assertThat(id1, not(equalTo(callUndertest(f2))));

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callUndertest(f2));
	}

	@Test
	public void itGivesNewIdWhenFilesDiverge () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callUndertest(f1);

		final File f2 = this.tmp.newFile("media-2.ext");
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callUndertest(f2));

		fillFile(f2);
		assertThat(id1, not(equalTo(callUndertest(f2))));
	}

	@Test
	public void itHandlesFileConvergingAndThenDiverging () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = callUndertest(f1);
		assertThat(id1, not(equalTo(callUndertest(f2))));

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callUndertest(f2));

		fillFile(f2);
		assertThat(id1, not(equalTo(callUndertest(f2))));
	}

	@Test
	public void itRevertsIdWhenFileContentReverts () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = callUndertest(f1);
		final String id2 = callUndertest(f2);
		assertThat(id1, not(equalTo(id2)));

		final File backup = this.tmp.newFile("backup");
		FileUtils.copyFile(f2, backup, false);

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callUndertest(f2));

		FileUtils.copyFile(backup, f2, false);
		assertEquals(id2, callUndertest(f2));
	}

	@Test
	public void itKeepsIdThroughMoveAndChange () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callUndertest(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		f2.delete();
		FileUtils.moveFile(f1, f2);
		assertEquals(id1, callUndertest(f2));

		fillFile(f2);
		assertEquals(id1, callUndertest(f2));
	}

	@Test
	public void itKeepsIdThroughCopyDeleteAndChange () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callUndertest(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		f2.delete();
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callUndertest(f2));

		f1.delete();
		assertEquals(id1, callUndertest(f2));

		fillFile(f2);
		assertEquals(id1, callUndertest(f2));
	}

	@Test
	public void itKeepsIdThroughCopyDeleteAndChangeMultiple () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callUndertest(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		f2.delete();
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callUndertest(f2));

		final File f3 = this.tmp.newFile("media-001.ext");
		f3.delete();
		FileUtils.copyFile(f1, f3, false);
		assertEquals(id1, callUndertest(f3));

		f1.delete();
		assertEquals(id1, callUndertest(f2));

		f3.delete();
		assertEquals(id1, callUndertest(f2));

		fillFile(f2);
		assertEquals(id1, callUndertest(f2));
	}

	@Test
	public void itStoresAndRetrivesDuration () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillisAsync(f1, 1234567890123L);
		this.durationBatchWriter.run();
		assertEquals(1234567890123L, this.undertest.readFileDurationMillis(f1));
	}

	@Test
	public void itReturnsZeroWhenFileSizeChangesUpdates () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillisAsync(f1, 1234567890123L);
		this.durationBatchWriter.run();
		FileUtils.writeStringToFile(f1, "abc", Charset.forName("UTF-8"));
		assertEquals(0L, this.undertest.readFileDurationMillis(f1));
	}

	@Test
	public void itUpdatesStoredDuration () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillisAsync(f1, 1234567890123L);
		this.durationBatchWriter.run();
		this.undertest.storeFileDurationMillisAsync(f1, 12345678901234L);
		this.durationBatchWriter.run();
	}

	private File mockMediaFile (final String name) throws IOException {
		final File f = this.tmp.newFile(name);
		fillFile(f);
		return f;
	}

	private void fillFile (final File f) throws IOException {
		final int l = (1024 * 10) + this.rnd.nextInt(1024 * 10);
		final byte[] b = new byte[l];
		this.rnd.nextBytes(b);
		FileUtils.writeByteArrayToFile(f, b);
	}

}

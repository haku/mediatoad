package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.vaguehope.dlnatoad.media.StoringMediaIdCallback;

public class MediaMetadataStoreTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private Random rnd;
	private File dbFile;
	private ScheduledExecutorService schEx;
	private MediaMetadataStore undertest;

	private Runnable durationWorker;

	@Before
	public void before () throws Exception {
		this.rnd = new Random();
		this.dbFile = this.tmp.newFile("id-db.db3");

		this.schEx = mock(ScheduledExecutorService.class);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer (final InvocationOnMock inv) throws Throwable {
				inv.getArgument(0, Runnable.class).run();
				return null;
			}
		}).when(this.schEx).execute(any(Runnable.class));

		this.undertest = new MediaMetadataStore(new MediaDb(this.dbFile), this.schEx, true);

		final ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);
		verify(this.schEx).scheduleWithFixedDelay(cap.capture(), eq(0L), eq(30L), any(TimeUnit.class));
		this.durationWorker = cap.getValue();
	}

	private String callIdForFile(final File file) throws IOException, InterruptedException {
		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		this.undertest.idForFile(file, cb);
		return cb.getMediaId();
	}

	@Test
	public void itConnectsToExistingDb () throws Exception {
		assertNotNull(new MediaMetadataStore(new MediaDb(this.dbFile), this.schEx, true));
	}

	@Test
	public void itReturnsSameIdForSameFile () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String res = callIdForFile(f1);
		assertNotNull(res);
		assertEquals(res, callIdForFile(f1));
	}

	@Test
	public void itReturnsDifferentIdsForDifferentFile () throws Exception {
		assertThat(callIdForFile(mockMediaFile("media-1.ext")),
				not(equalTo(callIdForFile(mockMediaFile("media-2.ext")))));
	}

	@Test
	public void itReturnsSameIdForIdenticalFiles () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = this.tmp.newFile("media-2.ext");
		FileUtils.copyFile(f1, f2, false);
		assertEquals(
				callIdForFile(f1),
				callIdForFile(f2));
	}

	@Ignore("It is debatable if this test should pass or not.")
	@Test
	public void itStoresTheSameIdInTheFilesTableForIdenticalFiles() throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = this.tmp.newFile("media-2.ext");
		FileUtils.copyFile(f1, f2, false);
		callIdForFile(f1);
		callIdForFile(f2);

		final FileData fd1 = getFileData(f1);
		final FileData fd2 = getFileData(f2);
		assertNotNull(fd1);
		assertNotNull(fd2);
		assertEquals(fd1.getId(), fd2.getId());
	}

	@Test
	public void itReturnsSameIdWhenFileContentStaysTheSame () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callIdForFile(f1);
		FileUtils.touch(f1);
		assertEquals(id1, callIdForFile(f1));
	}

	@Test
	public void itReturnsSameIdWhenFileContentChanges () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callIdForFile(f1);
		fillFile(f1);
		assertEquals(id1, callIdForFile(f1));
	}

	@Test
	public void itChangesToSameIdWhenFileBecomesSameAsAnother () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = callIdForFile(f1);
		assertThat(id1, not(equalTo(callIdForFile(f2))));

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callIdForFile(f2));
	}

	@Test
	public void itGivesNewIdWhenFilesDiverge () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callIdForFile(f1);

		final File f2 = this.tmp.newFile("media-2.ext");
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callIdForFile(f2));

		fillFile(f2);
		assertThat(id1, not(equalTo(callIdForFile(f2))));
	}

	@Test
	public void itHandlesFileConvergingAndThenDiverging () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = callIdForFile(f1);
		assertThat(id1, not(equalTo(callIdForFile(f2))));

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callIdForFile(f2));

		fillFile(f2);
		assertThat(id1, not(equalTo(callIdForFile(f2))));
	}

	@Test
	public void itRevertsIdWhenFileContentReverts () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = callIdForFile(f1);
		final String id2 = callIdForFile(f2);
		assertThat(id1, not(equalTo(id2)));

		final File backup = this.tmp.newFile("backup");
		FileUtils.copyFile(f2, backup, false);

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callIdForFile(f2));

		FileUtils.copyFile(backup, f2, false);
		assertEquals(id2, callIdForFile(f2));
	}

	@Test
	public void itKeepsIdThroughMoveAndChange () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callIdForFile(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		f1.renameTo(f2);
		assertFalse(f1.exists());
		assertEquals(id1, callIdForFile(f2));

		fillFile(f2);
		assertEquals(id1, callIdForFile(f2));
	}

	@Test
	public void itKeepsIdThroughCopyDeleteAndChange () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callIdForFile(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callIdForFile(f2));

		f1.delete();
		assertEquals(id1, callIdForFile(f2));

		fillFile(f2);
		assertEquals(id1, callIdForFile(f2));
	}

	@Test
	public void itKeepsIdThroughCopyDeleteAndChangeMultiple () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callIdForFile(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, callIdForFile(f2));

		final File f3 = this.tmp.newFile("media-001.ext");
		FileUtils.copyFile(f1, f3, false);
		assertEquals(id1, callIdForFile(f3));

		f1.delete();
		assertEquals(id1, callIdForFile(f2));

		f3.delete();
		assertEquals(id1, callIdForFile(f2));

		fillFile(f2);
		assertEquals(id1, callIdForFile(f2));
		assertEquals(id1, getFileData(f2).getId());
	}

	@Test
	public void itStoresAndRetrivesDuration () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillisAsync(f1, 1234567890123L);
		this.durationWorker.run();
		assertEquals(1234567890123L, this.undertest.readFileDurationMillis(f1));
	}

	@Test
	public void itReturnsZeroWhenFileSizeChangesUpdates () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillisAsync(f1, 1234567890123L);
		this.durationWorker.run();
		FileUtils.writeStringToFile(f1, "abc", Charset.forName("UTF-8"));
		assertEquals(0L, this.undertest.readFileDurationMillis(f1));
	}

	@Test
	public void itUpdatesStoredDuration () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillisAsync(f1, 1234567890123L);
		this.durationWorker.run();
		this.undertest.storeFileDurationMillisAsync(f1, 12345678901234L);
		this.durationWorker.run();
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

	public FileData getFileData(final File f) throws IOException, SQLException {
		try (final WritableMediaDb w = this.undertest.getMediaDb().getWritable()) {
			return w.readFileData(f);
		}
	}

}

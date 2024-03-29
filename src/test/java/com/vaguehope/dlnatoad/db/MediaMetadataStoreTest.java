package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
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

	private static final Random RND = new Random(System.currentTimeMillis());

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
		}).when(this.schEx).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

		this.undertest = new MediaMetadataStore(new MediaDb(this.dbFile), this.schEx, true);

		final ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);
		verify(this.schEx).scheduleWithFixedDelay(cap.capture(), eq(0L), eq(30L), any(TimeUnit.class));
		this.durationWorker = cap.getValue();
	}

	private String callIdForFile(final File file) throws IOException, InterruptedException {
		return callIdForFile(file, BigInteger.ZERO);
	}

	private String callIdForFile(final File file, final BigInteger auth) throws IOException, InterruptedException {
		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		this.undertest.idForFile(file, auth, cb);
		return cb.getMediaId();
	}

	@Test
	public void itConnectsToExistingDb () throws Exception {
		assertNotNull(new MediaMetadataStore(new MediaDb(this.dbFile), this.schEx, true));
	}

	@Test
	public void itStoresAuthForNewfileAndUpdatesForChangedAuth() throws Exception {
		final BigInteger auth1 = new BigInteger(128, RND);
		final File f1 = mockMediaFile("media-1.ext");
		callIdForFile(f1, auth1);
		assertEquals(auth1, this.undertest.getMediaDb().readFileAuth(f1));

		final BigInteger auth2 = new BigInteger(128, RND);
		assertNotEquals(auth1, auth2);
		callIdForFile(f1, auth2);
		assertEquals(auth2, this.undertest.getMediaDb().readFileAuth(f1));
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

	// This is needed for search to work correctly.
	@Test
	public void itUpdatesCanonicalWhenOnlyOneFileExists() throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = callIdForFile(f1);

		final File f2 = this.tmp.newFile("media-2.ext");
		FileUtils.copyFile(f1, f2, false);

		final String id2 = callIdForFile(f2);
		assertEquals(id1, id2);  // Overall thing returns canonical ID.

		// eg file is deleted while watcher is not running.
		f1.delete();
		// Do not update missing in DB cos that is not used in this logic.

		final String id2b = callIdForFile(f2);
		assertEquals(id2, id2b);

		final FileData fd1 = getFileData(f1);
		assertNull(fd1);  // should have been removed.

		final FileData fd2 = getFileData(f2);
		assertNotNull(fd2);

		try (final WritableMediaDb w = this.undertest.getMediaDb().getWritable()) {
			final String canonicalId = w.canonicalIdForHash(fd2.getHash());
			assertEquals(fd2.getId(), canonicalId);
		}
	}

	@Test
	public void itPropagatesFileGone() throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		callIdForFile(f1);

		this.undertest.fileGone(f1);
		assertTrue(getFileData(f1).isMissing());

		callIdForFile(f1);
		assertFalse(getFileData(f1).isMissing());
	}

	@Test
	public void itUpdateMd5IfNotSet() throws Exception {
		final File f = mockMediaFile("media-1.ext");
		FileUtils.writeStringToFile(f, "abcdefg", Charset.forName("UTF-8"));

		callIdForFile(f);
		// Simulate old DB with no MD5 values.
		try (final WritableMediaDb w = this.undertest.getMediaDb().getWritable()) {
			w.updateFileData(f, new FileData(f.length(), f.lastModified(), "sha1", null, "mime/type", "some-id", BigInteger.ZERO, false));
		}
		assertEquals(null, getFileData(f).getMd5());  // Verify simulation.

		callIdForFile(f);
		assertEquals("7ac66c0f148de9519b8bd264312c4d64", getFileData(f).getMd5());
	}

	@Test
	public void itUpdateMimeTypeIfNotSet() throws Exception {
		final File f = mockMediaFile("media-1.jpeg");
		callIdForFile(f);
		// Simulate old DB with no mimeType values.
		try (final WritableMediaDb w = this.undertest.getMediaDb().getWritable()) {
			w.updateFileData(f, new FileData(f.length(), f.lastModified(), "sha1", "md5", null, "some-id", BigInteger.ZERO, false));
		}
		assertEquals(null, getFileData(f).getMimeType());  // Verify simulation.

		callIdForFile(f);
		assertEquals("image/jpeg", getFileData(f).getMimeType());
	}

	@Test
	public void itRunsGenericCallback() throws Exception {
		final Runnable cb = mock(Runnable.class);
		this.undertest.putCallbackInQueue(cb);
		verify(cb).run();
	}

	@Test
	public void itStoresAndRetrivesInfo () throws Exception {
		final String id = "myfile1";
		final File f1 = mockMediaFile("media-1.ext");

		this.undertest.storeFileInfoAsync(id, f1, new FileInfo(1234567890123L, 640, 480));
		this.durationWorker.run();
		assertEquals(1234567890123L, this.undertest.readFileInfo(id, f1).getDurationMillis());
		assertEquals(640, this.undertest.readFileInfo(id, f1).getWidth());
		assertEquals(480, this.undertest.readFileInfo(id, f1).getHeight());
	}

	@Test
	public void itReturnsNullWhenFileSizeChangesUpdates () throws Exception {
		final String id = "myfile1";
		final File f1 = mockMediaFile("media-1.ext");

		this.undertest.storeFileInfoAsync(id, f1, new FileInfo(1234567890123L, 0, 0));
		this.durationWorker.run();
		FileUtils.writeStringToFile(f1, "abc", Charset.forName("UTF-8"));
		assertEquals(null, this.undertest.readFileInfo(id, f1));
	}

	@Test
	public void itUpdatesStoredDuration () throws Exception {
		final String id = "myfile1";
		final File f1 = mockMediaFile("media-1.ext");

		this.undertest.storeFileInfoAsync(id, f1, new FileInfo(1234567890123L, 0, 0));
		this.durationWorker.run();
		this.undertest.storeFileInfoAsync(id, f1, new FileInfo(12345678901234L, 0, 0));
		this.durationWorker.run();
		assertEquals(12345678901234L, this.undertest.readFileInfo(id, f1).getDurationMillis());
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

	private FileData getFileData(final File f) throws IOException, SQLException {
		try (final WritableMediaDb w = this.undertest.getMediaDb().getWritable()) {
			return w.readFileData(f);
		}
	}

}

package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MediaDbTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private File dbFile;
	private MediaDb undertest;

	@Before
	public void before () throws Exception {
		this.dbFile = this.tmp.newFile("id-db.db3");
		this.undertest = new MediaDb(this.dbFile);
	}

	@Test
	public void itDoesNotErrorOnEmptyTransaction() throws Exception {
		this.undertest.getWritable().close();
	}

	@Test
	public void itStoresFileData() throws Exception {
		final File file = new File("/media/foo.wav");
		final BigInteger auth = BigInteger.valueOf(123L);
		final FileData expected = new FileData(12, 123456, "myhash", "mymd5", "mime/type", "fileId", auth, false);
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, expected);
			w.updateFileAuth(file, auth);
		}
		assertEquals(expected, this.undertest.getFileData(file));

		final BigInteger newAuth = BigInteger.valueOf(456L);
		final FileData newExpected = new FileData(13, 654321, "newhash", "newmd5", "mime/other", "newFileId", newAuth, false);
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.updateFileData(file, newExpected);
			w.updateFileAuth(file, newAuth);
		}
		assertEquals(newExpected, this.undertest.getFileData(file));
	}

	@Test
	public void itGetsUndeletedVisibleTags() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", "", 1234567890L));
			assertTrue(w.addTag(fileId, "my-tag", "source", 1234567891L));
			assertTrue(w.addTag(fileId, "my-tag", "other", 1234567892L));
			assertTrue(w.addTag(fileId, "hidden", ".hidden", 1234567892L));
			assertTrue(w.mergeTag(fileId, "deleted", "", 1234567899L, true));
		}

		final Collection<Tag> actual = this.undertest.getTags(fileId, false, false);
		assertThat(actual, contains(
				new Tag("my-tag", "", 1234567890L, false),
				new Tag("my-tag", "other", 1234567892L, false),
				new Tag("my-tag", "source", 1234567891L, false)));
	}

	@Test
	public void itGetsGettingHiddenTags() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", "", 1234567890L));
			assertTrue(w.addTag(fileId, "hidden", ".hidden", 1234567892L));
		}

		final Collection<Tag> actual = this.undertest.getTags(fileId, true, false);
		assertThat(actual, contains(
				new Tag("hidden", ".hidden", 1234567892L, false),
				new Tag("my-tag", "", 1234567890L, false)));
	}

	@Test
	public void itAddsAndReadsAndRemovesATag() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
		}

		final Collection<Tag> expectedTags = this.undertest.getTags(fileId, true, false);
		final Tag existing = expectedTags.iterator().next();
		assertEquals("my-tag", existing.getTag());
		assertEquals(1234567890L, existing.getModified());
		assertEquals(false, existing.isDeleted());
		assertThat(expectedTags, hasSize(1));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagModifiedAndDeleted(fileId, existing.getTag(), existing.getCls(), true, 2345678901L);
		}

		final Collection<Tag> deletedTags = this.undertest.getTags(fileId, true, true);
		final Tag deleted = deletedTags.iterator().next();
		assertEquals("my-tag", deleted.getTag());
		assertEquals(2345678901L, deleted.getModified());
		assertEquals(true, deleted.isDeleted());
		assertThat(deletedTags, hasSize(1));

		assertThat(this.undertest.getTags(fileId, true, false), hasSize(0));
	}

	@Test
	public void itAddsADeletedTag() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.mergeTag(fileId, "my-tag", "", 1234567890L, true));
		}
		assertThat(this.undertest.getTags(fileId, true, true), contains(new Tag("my-tag", 1234567890L, true)));
	}

	@Test
	public void itDoesNotAddTagWithNoDate() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.mergeTag(fileId, "my-tag", "", 1234567890L, true));
			assertFalse(w.addTagIfNotDeleted(fileId, "my-tag", "", 9234567890L));
		}
		assertThat(this.undertest.getTags(fileId, true, true), contains(new Tag("my-tag", 1234567890L, true)));
	}

	@Test
	public void itDoesNotDuplicateTagsAndIsCaseInsenstive() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
			assertFalse(w.addTag(fileId, "my-tag", 1234567891L));
			assertFalse(w.addTag(fileId, "MY-TAG", 1234567892L));
		}
		assertThat(this.undertest.getTags(fileId, true, true), contains(new Tag("my-tag", 1234567890L, false)));
	}

	@Test
	public void itUpdatesTagCaseOnReAdd() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.mergeTag(fileId, "my-tag", "", 1234567890L, true));
		}
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			assertTrue(w.addTag(fileId, "My-Tag", "", 1234567891L));
		}
		assertThat(this.undertest.getTags(fileId, true, true), contains(new Tag("My-Tag", 1234567891L, false)));
	}

	@Test
	public void itAllowsAddingTheSameTagTwiceWithDifferentCls() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", "", 1234567889L));
			assertTrue(w.addTag(fileId, "my-tag", "c", 1234567890L));
			assertFalse(w.addTag(fileId, "my-tag", "", 1234567891L));
			assertFalse(w.addTag(fileId, "my-tag", "c", 1234567892L));
		}
		assertThat(this.undertest.getTags(fileId, true, true), contains(
				new Tag("my-tag", "", 1234567889L, false),
				new Tag("my-tag", "c", 1234567890L, false)
				));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagModifiedAndDeleted(fileId, "my-tag", "c", true, 1234567891L);
		}
		assertThat(this.undertest.getTags(fileId, true, true), contains(
				new Tag("my-tag", "", 1234567889L, false),
				new Tag("my-tag", "c", 1234567891L, true)
				));
	}

	@Test
	public void itDoesNotMergeOlderUpdates() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
			assertFalse(w.mergeTag(fileId, "my-tag", "", 1234567870L, true));
			assertFalse(w.mergeTag(fileId, "my-tag", "", 1234567890L, true));
		}
		assertThat(this.undertest.getTags(fileId, true, true), contains(new Tag("my-tag", 1234567890L, false)));
	}

	@Test
	public void itUndeletesWhenReAdding() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", "mime/type", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
		}

		final Collection<Tag> tags = this.undertest.getTags(fileId, true, false);
		final Tag tag = tags.iterator().next();
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagModifiedAndDeleted(fileId, tag.getTag(), tag.getCls(), true, 1234567891L);
		}

		final Collection<Tag> deleted = this.undertest.getTags(fileId, true, true);
		assertThat(deleted, hasSize(1));
		final Tag deletedTag = deleted.iterator().next();
		assertEquals(true, deletedTag.isDeleted());
		assertEquals(1234567891L, deletedTag.getModified());

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			assertTrue(w.addTag(fileId, "my-tag", 1234567892L));
		}

		final Collection<Tag> undeleted = this.undertest.getTags(fileId, true, true);
		assertThat(undeleted, hasSize(1));
		final Tag undeletedTag = undeleted.iterator().next();
		assertEquals(false, undeletedTag.isDeleted());
		assertEquals(1234567892L, undeletedTag.getModified());
	}

	@Test
	public void itMarkesFileAsMissing() throws Exception {
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", "mime/type", "myid", BigInteger.ZERO, false));
		}
		assertFalse(getFileData(file).isMissing());

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setFileMissing(file.getAbsolutePath(), true);
		}
		assertTrue(getFileData(file).isMissing());
	}

	@Test
	public void itInsertsInfos() throws Exception {
		final List<FileIdAndInfo> infos = ImmutableList.of(
				new FileIdAndInfo("id1", new File("/media/foo.jpg"), new FileInfo(0, 2000, 1000)),
				new FileIdAndInfo("id2", new File("/media/bar.jpg"), new FileInfo(0, 3000, 2000))
				);
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeInfos(infos);
		}

		assertEquals(new FileInfo(0, 2000, 1000), this.undertest.readInfoCheckingFileSize("id1", 0));
	}

	@Test
	public void itInsertsInfosWithSameIdTwiceIn1Batch() throws Exception {
		final List<FileIdAndInfo> infos = ImmutableList.of(
				new FileIdAndInfo("id1", new File("/media/foo.jpg"), new FileInfo(0, 2000, 1000)),
				new FileIdAndInfo("id1", new File("/media/bar.jpg"), new FileInfo(0, 2000, 1000))
				);
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeInfos(infos);
		}
	}

	@Test
	public void itGetsTopTags() throws Exception {
		final BigInteger auth = BigInteger.valueOf(234567);
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			for (int i = 0; i < 10; i++) {
				addMockFiles(w, "id-" + i, BigInteger.ZERO, "tag1");
				w.addTag("id-" + i, "tag1", "class.foo", 1234567891L);
				w.addTag("id-" + i, "hidden", ".class", 1234567891L);  // . classes should be excluded
			}
			for (int i = 10; i < 20; i++) {
				addMockFiles(w, "id-" + i, auth, "tag1", "tag2");
			}
			w.mergeTag("id-0", "deleted", "", 1234567890L, true);
		}
		assertThat(this.undertest.getTopTags(null, null, 10), contains(new TagFrequency("tag1", 10)));
		assertThat(this.undertest.getTopTags(ImmutableSet.of(auth), null, 10), contains(new TagFrequency("tag1", 20), new TagFrequency("tag2", 10)));
	}

	@Test
	public void itGetsTopTagsForSubDir() throws Exception {
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			for (int i = 0; i < 5; i++) {
				addMockFiles(w, "id-" + i, BigInteger.ZERO, "tag1");
			}
			for (int i = 5; i < 11; i++) {
				addMockFiles(w, "sub/dir/path/id-" + i, BigInteger.ZERO, "tag2");
			}
			for (int i = 11; i < 18; i++) {
				addMockFiles(w, "sub2/path/id-" + i, BigInteger.ZERO, "tag3");
			}
		}
		assertThat(this.undertest.getTopTags(null, null, 10), contains(
				new TagFrequency("tag3", 7),
				new TagFrequency("tag2", 6),
				new TagFrequency("tag1", 5)));
		assertThat(this.undertest.getTopTags(null, "/media/sub", 10), contains(
				new TagFrequency("tag2", 6)));
	}

	@Test
	public void itGetsAllTags() throws Exception {
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			for (int i = 0; i < 10; i++) {
				addMockFiles(w, "id-" + i, BigInteger.ZERO, "foobar");
				w.addTag("id-" + i, "foo hidden", ".class", 1234567891L);  // . classes should be excluded
				w.mergeTag("id-" + i, "foodeleted", "", 1234567890L, true);
			}
		}
		assertThat(this.undertest.getAllTagsNotMissingNotDeleted(), contains(
				new TagFrequency("foobar", 10)));
	}

	@Test
	public void itRecordsPlayback() throws Exception {
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.recordPlayback("incomplete", 1234567890L, false);
			w.recordPlayback("complete", 2234567890L, true);
		}

		final Playback incomplete1 = this.undertest.getPlayback("incomplete");
		assertEquals(1234567890L, incomplete1.getDateLastPlayed());
		assertEquals(1, incomplete1.getStartCount());
		assertEquals(0, incomplete1.getCompleteCount());

		final Playback complete1 = this.undertest.getPlayback("complete");
		assertEquals(2234567890L, complete1.getDateLastPlayed());
		assertEquals(1, complete1.getStartCount());
		assertEquals(1, complete1.getCompleteCount());

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.recordPlayback("incomplete", 1534567890L, false);
			w.recordPlayback("complete", 2534567890L, true);
		}

		final Playback incomplete2 = this.undertest.getPlayback("incomplete");
		assertEquals(1534567890L, incomplete2.getDateLastPlayed());
		assertEquals(2, incomplete2.getStartCount());
		assertEquals(0, incomplete2.getCompleteCount());

		final Playback complete2 = this.undertest.getPlayback("complete");
		assertEquals(2534567890L, complete2.getDateLastPlayed());
		assertEquals(2, complete2.getStartCount());
		assertEquals(2, complete2.getCompleteCount());
	}

	@Test
	public void itMarkesFileAsExcluded() throws Exception {
		assertNull(this.undertest.getPlayback("myid"));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setFileExcluded("myid", false, true);
		}
		assertFalse(this.undertest.getPlayback("myid").isExcluded());

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setFileExcluded("myid", true, true);
		}
		assertTrue(this.undertest.getPlayback("myid").isExcluded());
	}

	@Ignore
	@Test
	public void benchmarkPlaybackRead() throws Exception {
		final List<String> ids = new ArrayList<>();
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			for (int i = 0; i < 100000; i++) {
				final String id = RandomStringUtils.insecure().nextAlphabetic(15);
				ids.add(id);
				w.recordPlayback(id, 1234567890L, false);
			}
		}
		System.out.println("item count: " + ids.size());

		final int itterations = 10;
		long totalTime = 0;
		for (int i = 0; i < itterations; i++) {
			final long start = System.nanoTime();
			final int fromIndex = (int) (ids.size() * 0.25);
			this.undertest.getPlayback(ids.subList(fromIndex, fromIndex + 500));
			totalTime += System.nanoTime() - start;
		}
		System.out.println("benchmark playback read: " + TimeUnit.NANOSECONDS.toMillis(totalTime / itterations) + " ms");
	}

	@Test
	public void itSetsAndReadsPrefs() throws Exception {
		final String id1 = "486023200074112812592441620153605687291657744882-somewhere";
		final String id2 = "457356430841943070727738514685648663226656335856-somewhere_else";

		assertThat(this.undertest.getNodePrefs(id1).entrySet(), hasSize(0));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setNodePref(id1, "my_pref_1", "true");
			w.setNodePref(id2, "my_pref_1", "true");
			w.setNodePref(id1, "my_pref_2", "false");
		}
		assertEquals(ImmutableMap.of("my_pref_1", "true", "my_pref_2", "false"), this.undertest.getNodePrefs(id1));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setNodePref(id1, "my_pref_2", "true");
		}
		assertEquals(ImmutableMap.of("my_pref_1", "true", "my_pref_2", "true"), this.undertest.getNodePrefs(id1));

		assertEquals(ImmutableMap.of(id1, "true", id2, "true"), this.undertest.getAllNodePref("my_pref_1"));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setNodePref(id1, "my_pref_1", null);
		}
		assertEquals(ImmutableMap.of(id2, "true"), this.undertest.getAllNodePref("my_pref_1"));
	}

	private static void addMockFiles(final WritableMediaDb w, final String id, final BigInteger auth, final String... tags) throws SQLException {
		final File f = new File("/media/" + id + ".wav");
		w.storeFileData(f, new FileData(12, 123456, "myhash-" + id, "mime/type", "mymd5=" + id, id, null, false));
		w.updateFileAuth(f, auth);
		for (final String tag : tags) {
			assertTrue(w.addTag(id, tag, 1234567890L));
		}
	}

	private FileData getFileData(final File f) throws IOException, SQLException {
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			return w.readFileData(f);
		}
	}

}

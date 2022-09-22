package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.Collection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
	public void itAddsAndReadsAndRemovesATag() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
		}

		final Collection<Tag> expectedTags = this.undertest.getTags(fileId, false);
		final Tag existing = expectedTags.iterator().next();
		assertEquals("my-tag", existing.getTag());
		assertEquals(1234567890L, existing.getModified());
		assertEquals(false, existing.isDeleted());
		assertThat(expectedTags, hasSize(1));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagModifiedAndDeleted(fileId, existing.getTag(), existing.getCls(), true, 2345678901L);
		}

		final Collection<Tag> deletedTags = this.undertest.getTags(fileId, true);
		final Tag deleted = deletedTags.iterator().next();
		assertEquals("my-tag", deleted.getTag());
		assertEquals(2345678901L, deleted.getModified());
		assertEquals(true, deleted.isDeleted());
		assertThat(deletedTags, hasSize(1));

		assertThat(this.undertest.getTags(fileId, false), hasSize(0));
	}

	@Test
	public void itAddsADeletedTag() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", fileId, BigInteger.ZERO, false));
			assertTrue(w.mergeTag(fileId, "my-tag", "", 1234567890L, true));
		}
		assertThat(this.undertest.getTags(fileId, true), contains(new Tag("my-tag", 1234567890L, true)));
	}

	@Test
	public void itDoesNotAddTagWithNoDate() throws Exception {
		final String fileId = "myid";
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", "mymd5", fileId, BigInteger.ZERO, false));
			assertTrue(w.mergeTag(fileId, "my-tag", "", 1234567890L, true));
			assertFalse(w.addTagIfNotDeleted(fileId, "my-tag", "", 9234567890L));
		}
		assertThat(this.undertest.getTags(fileId, true), contains(new Tag("my-tag", 1234567890L, true)));
	}

	@Test
	public void itDoesNotDuplicateTagsAndIsCaseInsenstive() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
			assertFalse(w.addTag(fileId, "my-tag", 1234567891L));
			assertFalse(w.addTag(fileId, "MY-TAG", 1234567892L));
		}
		assertThat(this.undertest.getTags(fileId, true), contains(new Tag("my-tag", 1234567890L, false)));
	}

	@Test
	public void itAllowsAddingTheSameTagTwiceWithDifferentCls() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", "", 1234567889L));
			assertTrue(w.addTag(fileId, "my-tag", "c", 1234567890L));
			assertFalse(w.addTag(fileId, "my-tag", "", 1234567891L));
			assertFalse(w.addTag(fileId, "my-tag", "c", 1234567892L));
		}
		assertThat(this.undertest.getTags(fileId, true), contains(
				new Tag("my-tag", "", 1234567889L, false),
				new Tag("my-tag", "c", 1234567890L, false)
				));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagModifiedAndDeleted(fileId, "my-tag", "c", true, 1234567891L);
		}
		assertThat(this.undertest.getTags(fileId, true), contains(
				new Tag("my-tag", "", 1234567889L, false),
				new Tag("my-tag", "c", 1234567891L, true)
				));
	}

	@Test
	public void itDoesNotMergeOlderUpdates() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
			assertFalse(w.mergeTag(fileId, "my-tag", "", 1234567870L, true));
			assertFalse(w.mergeTag(fileId, "my-tag", "", 1234567890L, true));
		}
		assertThat(this.undertest.getTags(fileId, true), contains(new Tag("my-tag", 1234567890L, false)));
	}

	@Test
	public void itUndeletesWhenReAdding() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", fileId, BigInteger.ZERO, false));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
		}

		final Collection<Tag> tags = this.undertest.getTags(fileId, false);
		final Tag tag = tags.iterator().next();
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagModifiedAndDeleted(fileId, tag.getTag(), tag.getCls(), true, 1234567891L);
		}

		final Collection<Tag> deleted = this.undertest.getTags(fileId, true);
		assertThat(deleted, hasSize(1));
		final Tag deletedTag = deleted.iterator().next();
		assertEquals(true, deletedTag.isDeleted());
		assertEquals(1234567891L, deletedTag.getModified());

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			assertTrue(w.addTag(fileId, "my-tag", 1234567892L));
		}

		final Collection<Tag> undeleted = this.undertest.getTags(fileId, true);
		assertThat(undeleted, hasSize(1));
		final Tag undeletedTag = undeleted.iterator().next();
		assertEquals(false, undeletedTag.isDeleted());
		assertEquals(1234567892L, undeletedTag.getModified());
	}

	@Test
	public void itMarkesFileAsMissing() throws Exception {
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", "mymd5", "myid", BigInteger.ZERO, false));
		}
		assertFalse(getFileData(file).isMissing());

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setFileMissing(file.getAbsolutePath(), true);
		}
		assertTrue(getFileData(file).isMissing());
	}

	@Test
	public void itGetsTopTags() throws Exception {
		final BigInteger auth = BigInteger.valueOf(234567);
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			for (int i = 0; i < 10; i++) {
				addMockFiles(w, "id-" + i, BigInteger.ZERO, "tag1");
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

	private static void addMockFiles(final WritableMediaDb w, final String id, final BigInteger auth, final String... tags) throws SQLException {
		final File f = new File("/media/" + id + ".wav");
		w.storeFileData(f, new FileData(12, 123456, "myhash-" + id, "mymd5=" + id, id, null, false));
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

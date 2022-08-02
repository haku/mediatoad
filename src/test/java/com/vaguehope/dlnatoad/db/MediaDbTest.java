package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
			w.storeFileData(new File("/media/foo.wav"), new FileData(12, 123456, "myhash", fileId));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
		}

		final Collection<Tag> expectedTags = this.undertest.getTags(fileId, false);
		final Tag existing = expectedTags.iterator().next();
		assertEquals("my-tag", existing.getTag());
		assertEquals(1234567890L, existing.getModified());
		assertEquals(false, existing.isDeleted());
		assertThat(expectedTags, hasSize(1));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagDeleted(fileId, existing.getTag(), true, 2345678901L);
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
	public void itDoesNotDuplicateTagsAndIsCaseInsenstive() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", fileId));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
			assertFalse(w.addTag(fileId, "my-tag", 1234567891L));
			assertFalse(w.addTag(fileId, "MY-TAG", 1234567892L));
		}
		assertThat(this.undertest.getTags(fileId, true), hasSize(1));
	}

	@Test
	public void itUndeletesWhenReadding() throws Exception {
		final String fileId = "myid";
		final File file = new File("/media/foo.wav");
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.storeFileData(file, new FileData(12, 123456, "myhash", fileId));
			assertTrue(w.addTag(fileId, "my-tag", 1234567890L));
		}

		final Collection<Tag> tags = this.undertest.getTags(fileId, false);
		final Tag tag = tags.iterator().next();
		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagDeleted(fileId, tag.getTag(), true, 1234567891L);
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

}

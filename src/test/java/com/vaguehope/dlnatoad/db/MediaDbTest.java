package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;

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
			w.addTag(fileId, "my-tag", 1234567890L);
		}

		final Collection<Tag> expectedTags = this.undertest.getTags(fileId, false);
		final Tag existing = expectedTags.iterator().next();
		assertThat(existing.getId(), greaterThan(0L));
		assertEquals("my-tag", existing.getTag());
		assertEquals(1234567890L, existing.getModified());
		assertEquals(false, existing.isDeleted());
		assertThat(expectedTags, hasSize(1));

		try (final WritableMediaDb w = this.undertest.getWritable()) {
			w.setTagDeleted(existing, true, 2345678901L);
		}

		final Collection<Tag> deletedTags = this.undertest.getTags(fileId, true);
		final Tag deleted = deletedTags.iterator().next();
		assertEquals(existing.getId(), deleted.getId());
		assertEquals("my-tag", deleted.getTag());
		assertEquals(2345678901L, deleted.getModified());
		assertEquals(true, deleted.isDeleted());
		assertThat(deletedTags, hasSize(1));

		assertThat(this.undertest.getTags(fileId, false), hasSize(0));
	}

}

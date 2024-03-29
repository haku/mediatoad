package com.vaguehope.dlnatoad.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MockContent;

public class DbCleanerTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private InMemoryMediaDb mediaDb;
	private ContentTree contentTree;
	private MockContent mockContent;
	private DbCleaner undertest;

	@Before
	public void before () throws Exception {

		this.mediaDb = new InMemoryMediaDb();
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.undertest = new DbCleaner(this.contentTree, this.mediaDb, true);
	}

	@Test
	public void itMarksMissingFileAsMissing() throws Exception {
		final ContentItem i1 = this.mockContent.givenMockItem();
		final ContentItem i2 = this.mockContent.givenMockItem();
		final File f3 = this.tmp.newFile("media.wav");

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.storeFileData(i1.getFile(), new FileData(12, 123456, "myhash", "md5", "mime/type", i1.getId(), BigInteger.ZERO, false));
			w.storeFileData(i2.getFile(), new FileData(13, 123457, "myhash2", "md5-2", "mime/type", i2.getId(), BigInteger.ZERO, false));
			w.storeFileData(f3, new FileData(12, 123458, "myhash", "md5", "mime/type", i1.getId() + "-dupe", BigInteger.ZERO, false));
		}
		assertFalse(getFileData(i1.getFile()).isMissing());
		assertFalse(getFileData(i2.getFile()).isMissing());
		assertFalse(getFileData(f3).isMissing());

		assertTrue(i2.getFile().delete());
		assertEquals(1, this.contentTree.removeFile(i2.getFile()));  // Simulate MediaIndex.fileGone().

		this.undertest.cleanDb();

		assertFalse(getFileData(i1.getFile()).isMissing());
		assertTrue(getFileData(i2.getFile()).isMissing());

		// Although this dupe file will not be in ContentTree, it should not be marked as missing.
		assertFalse(getFileData(f3).isMissing());
	}

	private FileData getFileData(final File f) throws IOException, SQLException {
		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			return w.readFileData(f);
		}
	}

}

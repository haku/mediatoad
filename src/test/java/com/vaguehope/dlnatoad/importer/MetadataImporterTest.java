package com.vaguehope.dlnatoad.importer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.vaguehope.dlnatoad.db.InMemoryMediaDb;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.MediaMetadataStore;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.media.StoringMediaIdCallback;
import com.vaguehope.dlnatoad.util.HashHelper;
import com.vaguehope.dlnatoad.util.Time;
import com.vaguehope.dlnatoad.util.Time.FakeTime;

public class MetadataImporterTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private FakeTime time;
	private MediaDb mediaDb;
	private ScheduledExecutorService schEx;
	private MediaMetadataStore mediaMetadataStore;
	private File dropDir;
	private MetadataImporter undertest;


	@Before
	public void before() throws Exception {
		this.schEx = mock(ScheduledExecutorService.class);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer (final InvocationOnMock inv) throws Throwable {
				inv.getArgument(0, Runnable.class).run();
				return null;
			}
		}).when(this.schEx).execute(any(Runnable.class));

		this.time = new Time.FakeTime();
		this.mediaDb = new InMemoryMediaDb();
		this.mediaMetadataStore = new MediaMetadataStore(this.mediaDb, this.schEx, true);
		this.dropDir = this.tmp.newFolder();
		this.undertest = new MetadataImporter(this.dropDir, this.mediaDb, true, this.time);

		FileUtils.writeStringToFile(new File(this.dropDir, "ignore-me.txt"), "abc[", "UTF-8");
		assertTrue(new File(this.dropDir, "ignore-me-dir").mkdir());
	}

	@Test
	public void itImportsMinimalDropFile() throws Exception {
		final long now = this.time.now();

		final File mediaFileA = this.tmp.newFile();
		FileUtils.writeStringToFile(mediaFileA, "abc123", "UTF-8");
		final String sha1 = HashHelper.sha1(mediaFileA).toString(16);

		final File mediaFileB = this.tmp.newFile();
		FileUtils.writeStringToFile(mediaFileB, "def456", "UTF-8");
		final String md5 = HashHelper.md5(mediaFileB).toString(16);
		assertNotEquals(sha1, md5);

		final StoringMediaIdCallback cbA = new StoringMediaIdCallback();
		this.mediaMetadataStore.idForFile(mediaFileA, BigInteger.ZERO, cbA);
		final String fileIdA = cbA.getMediaId();

		final StoringMediaIdCallback cbB = new StoringMediaIdCallback();
		this.mediaMetadataStore.idForFile(mediaFileB, BigInteger.ZERO, cbB);
		final String fileIdB = cbB.getMediaId();
		assertNotEquals(fileIdA, fileIdB);

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.addTag(fileIdA, "should-not-change", now);
			w.addTag(fileIdA, "will-be-deleted", now);
		}

		final String sha1UpperCase = sha1.toUpperCase();
		assertNotEquals(sha1UpperCase, sha1);  // Check they do not match anymore.

		final String md5UpperCase = md5.toUpperCase();
		assertNotEquals(md5UpperCase, md5);  // Check they do not match anymore.

		final File dropFile = new File(this.dropDir, "my drop file.json");
		FileUtils.writeStringToFile(dropFile, "["
				+ "{\"sha1\": \"" + sha1UpperCase + "\", \"tags\": ["
				+ "{\"tag\":\"foo\",\"mod\":123,\"del\":false},"
				+ "{\"tag\":\"will-be-deleted\",\"mod\":" + (now + 1) + ",\"del\":true},"
				+ "{\"tag\":\"should-not-change\",\"del\":true},"  // will be ignored.
				+ "{\"tag\":\"should-not-change\",\"mod\":" + now + ",\"del\":false}," // will be ignored.
				+ "{\"tag\":\"should-not-change\",\"mod\":" + now + ",\"del\":true}," // will be ignored.
				+ "{\"tag\":\"no-mod-date\",\"del\":false},"       // will be added if not exists.
				+ "{\"tag\":\"no-mod-deleted\",\"del\":true},"     // will be ignored.
				+ "{\"tag\":\"bat\",\"mod\":123,\"del\":true}"
				+ "]},"
				+ "{\"sha1\": \"abc123\", \"tags\": ["
				+ "{\"tag\":\"should-not-be-imported\",\"mod\":123,\"del\":false},"
				+ "]},"
				+ "{\"md5\": \"" + md5UpperCase + "\", \"tags\": ["
				+ "{\"tag\":\"new-tag-via-md5\",\"cls\":\"place\"}"
				+ "]},"
				+ "{\"md5\": \"abc123\", \"tags\": ["
				+ "{\"tag\":\"should-not-be-imported\",\"mod\":123,\"del\":false},"
				+ "]}"
				+ "]", "UTF-8");

		this.undertest.processDropDir();
		assertFalse(dropFile.exists());
		assertTrue(new File(this.dropDir, "my drop file.json.imported").exists());

		assertThat(this.mediaDb.getTags(fileIdA, true), containsInAnyOrder(
				new Tag("foo", 123, false),
				new Tag("should-not-change", now, false),
				new Tag("will-be-deleted", now + 1, true),
				new Tag("no-mod-date", now, false),
				new Tag("bat", 123, true)
				));

		assertThat(this.mediaDb.getTags(fileIdB, true), containsInAnyOrder(
				new Tag("new-tag-via-md5", "place", now, false)
				));

		assertEquals(5, this.undertest.getCountOfImportedTags());
	}

	@Test
	public void itHandlesLeadingZeros() throws Exception {
		final File mediaFile = this.tmp.newFile();
		FileUtils.writeStringToFile(mediaFile, "29870140786256099922262137536303123045", "UTF-8");
		final String sha1 = HashHelper.sha1(mediaFile).toString(16);
		assertEquals(38, sha1.length());  // Assert less than 40.
		assertFalse(sha1.startsWith("0"));

		final String fullLengthSha1 = "00" + sha1;
		assertEquals(40, fullLengthSha1.length());

		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		this.mediaMetadataStore.idForFile(mediaFile, BigInteger.ZERO, cb);
		final String fileId = cb.getMediaId();

		final File dropFile = new File(this.dropDir, "my drop file.json");
		FileUtils.writeStringToFile(dropFile, "["
				+ "{\"sha1\": \"" + fullLengthSha1 + "\", \"tags\": ["
						+ "{\"tag\":\"foo\",\"mod\":123,\"del\":false},"
						+ "{\"tag\":\"bar\",\"mod\":123,\"del\":false},"
						+ "{\"tag\":\"bat\",\"mod\":123,\"del\":false}"
						+ "]}"
				+ "]", "UTF-8");

		this.undertest.processDropDir();

		final Set<String> actual = tagsAsSet(this.mediaDb.getTags(fileId, false));
		assertThat(actual, containsInAnyOrder("foo", "bar", "bat"));
	}

	@Test
	public void itRenamesInvalidFiles() throws Exception {
		final File dropFile = new File(this.dropDir, "my drop file.json");
		FileUtils.writeStringToFile(dropFile, "a[]", "UTF-8");

		this.undertest.processDropDir();

		assertFalse(dropFile.exists());
		assertTrue(new File(this.dropDir, "my drop file.json.failed").exists());
	}

	private static Set<String> tagsAsSet(final Collection<Tag> tags) {
		final Set<String> ret = new HashSet<>();
		for (final Tag tag : tags) {
			ret.add(tag.getTag());
		}
		return ret;
	}

}

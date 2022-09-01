package com.vaguehope.dlnatoad.db;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.vaguehope.dlnatoad.media.StoringMediaIdCallback;

public class MockMediaMetadataStore extends MediaMetadataStore {

	private final TemporaryFolder tmp;

	public MockMediaMetadataStore(final TemporaryFolder tmp) throws SQLException {
		super(new InMemoryMediaDb(), makeExSvs(), true);
		this.tmp = tmp;
	}

	private static ScheduledExecutorService makeExSvs() {
		final ScheduledExecutorService schEx = mock(ScheduledExecutorService.class);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer (final InvocationOnMock inv) throws Throwable {
				inv.getArgument(0, Runnable.class).run();
				return null;
			}
		}).when(schEx).execute(any(Runnable.class));
		return schEx;
	}

	public String addFileWithRandomTags() throws IOException, InterruptedException, SQLException {
		final int count = RandomUtils.nextInt(3, 10);
		final String[] tags = new String[count];
		for (int i = 0; i < count; i++) {
			tags[i] = RandomStringUtils.randomPrint(20, 50);
		}
		return addFileWithTags(tags);
	}

	public String addFileWithTags(final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(RandomStringUtils.randomAlphanumeric(10, 50), ".ext", tags);
	}

	public String addFileWithName(String nameFragment) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, ".ext");
	}

	public String addFileWithName(String nameFragment, String nameSuffex) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, nameSuffex);
	}

	public String addFileWithNameAndTags(String nameFragment, final String... tags) throws IOException, InterruptedException, SQLException {
		return addFileWithNameExtAndTags(nameFragment, ".ext", tags);
	}

	public String addFileWithNameExtAndTags(String nameFragment, String nameSuffex, final String... tags) throws IOException, InterruptedException, SQLException {
		final File mediaFile = File.createTempFile("mock_media_" + nameFragment, nameSuffex, this.tmp.getRoot());
		FileUtils.writeStringToFile(mediaFile, RandomStringUtils.randomPrint(10, 50), StandardCharsets.UTF_8);

		final StoringMediaIdCallback cb = new StoringMediaIdCallback();
		idForFile(mediaFile, cb);
		final String fileId = cb.getMediaId();

		try (final WritableMediaDb w = getMediaDb().getWritable()) {
			for (final String tag : tags) {
				w.addTag(fileId, tag, System.currentTimeMillis());
			}
		}

		return fileId;
	}

}

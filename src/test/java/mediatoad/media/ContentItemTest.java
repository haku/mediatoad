package mediatoad.media;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ContentItemTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itReadsFileAttributes() throws Exception {
		final File file = this.tmp.newFile();
		final BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);

		final ContentItem item = new ContentItem("id", "pid", "title", file, MediaFormat.GIF);
		assertEquals(file.length(), item.getFileLength());
		assertEquals(file.lastModified(), item.getModifiedMillis());
		assertEquals(attrs.creationTime().toMillis(), item.getCreatedMillis());
	}

}

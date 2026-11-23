package mediatoad.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import mediatoad.auth.Users.User;

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

	@Test
	public void itChecksTagAuth() throws Exception {
		final User noTagsUser = new User(null, null, null, null);
		final User withTagsUser = new User(null, null, null, Set.of("authtag1", "authtag2"));

		final File file = this.tmp.newFile();
		final ContentItem item = new ContentItem("id", "pid", "title", file, MediaFormat.GIF);
		assertFalse(item.isUserAuth(noTagsUser));
		assertFalse(item.isUserAuth(withTagsUser));

		item.setAuthTags(Set.of("authtag2"));
		assertFalse(item.isUserAuth(noTagsUser));
		assertTrue(item.isUserAuth(withTagsUser));
	}

}

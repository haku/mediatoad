package mediatoad.ui;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.eclipse.jetty.util.resource.Resource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import mediatoad.FakeServletCommon;
import mediatoad.TestsWithHttpServer;
import mediatoad.auth.AuthList;
import mediatoad.media.ContentGroup;
import mediatoad.media.ContentItem;
import mediatoad.media.ContentNode;
import mediatoad.media.ContentTree;
import mediatoad.media.MediaFormat;
import mediatoad.media.MockContent;
import mediatoad.media.ThumbnailGenerator;

public class ThumbsServletTest extends TestsWithHttpServer {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MockContent mockContent;
	private ThumbnailGenerator thumbnailGenerator;
	private ThumbsServlet undertest;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.thumbnailGenerator = mock(ThumbnailGenerator.class);
		this.mockContent = new MockContent(this.contentTree);
		this.undertest = new ThumbsServlet(this.contentTree, this.thumbnailGenerator, FakeServletCommon.make(this.contentTree));
	}

	@Test
	public void itDoesSomething() throws Exception {
		final ContentItem item = this.mockContent.givenMockItems(1).get(0);
		final File thumbFile = this.tmp.newFile();
		when(this.thumbnailGenerator.supported(ContentGroup.VIDEO, true)).thenReturn(true);
		when(this.thumbnailGenerator.generate(item)).thenReturn(thumbFile);

		@SuppressWarnings("resource")
		final Resource res = this.undertest.getResource("/" + item.getId());
		assertEquals(thumbFile, res.getFile());
	}

	@Test
	public void itChecksPermissions() throws Exception {
		startServer(this.undertest, "/t/*");

		final AuthList allowAuthlist = mock(AuthList.class);
		when(allowAuthlist.hasUser("shork")).thenReturn(true);

		final ContentNode dir1 = this.mockContent.addMockDir("dir1", allowAuthlist);
		final ContentItem item = this.mockContent.addMockItem(MediaFormat.JPEG, "item1", dir1);

		when(this.thumbnailGenerator.supported(ContentGroup.IMAGE, true)).thenReturn(true);
		final String fileName = item.getFile().getName();
		when(this.thumbnailGenerator.generate(item)).thenReturn(this.tmp.newFile(fileName));  // needs proper filename with ext for detecting mime type when serving.

		assertRequestResponseCode("/t/" + item.getId(), 401);

		setAuthUsername("shork");
		assertRequestResponseCode("/t/" + item.getId(), 200);
	}

}

package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.eclipse.jetty.util.resource.Resource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MockContent;
import com.vaguehope.dlnatoad.media.ThumbnailGenerator;

public class ThumbsServletTest {

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
		this.undertest = new ThumbsServlet(this.contentTree, this.thumbnailGenerator);
	}

	@Test
	public void itDoesSomething() throws Exception {
		final ContentItem item = this.mockContent.givenMockItems(1).get(0);
		final File thumbFile = this.tmp.newFile();
		when(this.thumbnailGenerator.generate(item)).thenReturn(thumbFile);

		@SuppressWarnings("resource")
		final Resource res = this.undertest.getResource("/" + item.getId());
		assertEquals(thumbFile, res.getFile());
	}

}

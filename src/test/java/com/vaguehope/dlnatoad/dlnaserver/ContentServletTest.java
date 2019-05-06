package com.vaguehope.dlnatoad.dlnaserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;

import org.eclipse.jetty.util.resource.Resource;
import org.junit.Before;
import org.junit.Test;

import com.vaguehope.dlnatoad.media.MediaFormat;

public class ContentServletTest {

	private ContentTree contentTree;
	private ContentServlet undertest;

	@Before
	public void before () throws Exception {
		this.contentTree = new ContentTree();
		this.undertest = new ContentServlet(this.contentTree, new ContentServingHistory(), true);
	}

	@Test
	public void itResolvesMediaResource () throws Exception {
		String id = "some_id";
		File file = new File(id);
		this.contentTree.addNode(new ContentNode(id, null, file, MediaFormat.OGG));

		Resource res = this.undertest.getResource("/" + id);
		assertEquals(file.getName(), res.getFile().getName());
	}

	@Test
	public void itResolvesMediaResourceWithFileExtension () throws Exception {
		String id = "some_id";
		File file = new File(id);
		this.contentTree.addNode(new ContentNode(id, null, file, MediaFormat.OGG));

		Resource res = this.undertest.getResource("/" + id + ".foo");
		assertEquals(file.getName(), res.getFile().getName());
	}

	@Test
	public void itReturnsNullForNotFound () throws Exception {
		assertNull(this.undertest.getResource("/some_id"));
	}

}

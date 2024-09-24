package com.vaguehope.dlnatoad.webdav;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.MockMediaMetadataStore;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MockContent;

/*
 * curl http://192.168.1.9:8192/dav/search
 * getPathInfo(): null
 *
 * curl http://192.168.1.9:8192/dav/search/
 * getPathInfo(): /
 *
 * curl http://192.168.1.9:8192/dav/search/t=foo/dir/file
 * getPathInfo():                                     /t=foo/dir/file
 * getRequestURI():                        /dav/search/t=foo/dir/file
 * getRequestURL(): http://192.168.1.9:8192/dav/search/t=foo/dir/file
 */

public class WebdavSearchServletTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MockContent mockContent;
	private MockMediaMetadataStore mockMediaMetadataStore;
	private MediaDb db;
	private WebdavSearchServlet undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);
		this.mockMediaMetadataStore = MockMediaMetadataStore.withMockExSvc(this.tmp);
		this.db = this.mockMediaMetadataStore.getMediaDb();

		this.undertest = new WebdavSearchServlet(this.contentTree, this.db);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}


	@Test
	public void itDoesSomething() throws Exception {
		final ContentItem i = mockItem("thing 0", "foo");
		System.out.println(i.getFile().getAbsolutePath());

		this.req.setMethod("GET");
		this.req.setPathInfo("/t=foo/dir/file");
		this.req.addHeader("Depth", "0");

		this.undertest.service(this.req, this.resp);

		assertThat(this.resp.getStatus(), equalTo(200));
		assertThat(this.resp.getContentAsString(), containsString(""));
	}

	private ContentItem mockItem(final String name, final String... tags) throws Exception {
		final String id = this.mockMediaMetadataStore.addFileWithNameAndSuffexAndTags(name, ".jpeg", tags);
		final File file = new File(this.db.getFilePathForId(id));
		final ContentItem item = new ContentItem(id, "0", name, file, MediaFormat.JPEG);
		this.contentTree.addItem(item);
		return item;
	}


}

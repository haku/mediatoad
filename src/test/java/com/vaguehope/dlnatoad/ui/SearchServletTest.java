package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jupnp.UpnpService;

import com.vaguehope.common.servlet.MockHttpServletRequest;
import com.vaguehope.common.servlet.MockHttpServletResponse;
import com.vaguehope.dlnatoad.FakeServletCommon;
import com.vaguehope.dlnatoad.db.DbCache;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.MockMediaMetadataStore;
import com.vaguehope.dlnatoad.dlnaserver.SearchEngine;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentServlet;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.ThumbnailGenerator;
import com.vaguehope.dlnatoad.rpc.client.RpcClient;

public class SearchServletTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ServletCommon servletCommon;
	private ContentTree contentTree;
	private ContentServlet contentServlet;
	private MockMediaMetadataStore mockMediaMetadataStore;
	private MediaDb db;
	private DbCache dbCache;
	private UpnpService upnpService;
	private RpcClient rpcClient;
	private ThumbnailGenerator thumbnailGenerator;
	private SearchEngine searchEngine;
	private SearchServlet undertest;


	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.servletCommon = FakeServletCommon.make(this.contentTree);

		this.contentTree = new ContentTree();
		this.contentServlet = mock(ContentServlet.class);
		this.mockMediaMetadataStore = MockMediaMetadataStore.withMockExSvc(this.tmp);
		this.db = this.mockMediaMetadataStore.getMediaDb();

		this.dbCache = mock(DbCache.class);
		when(this.dbCache.searchTopTags(anySet(), anyString())).thenReturn(Collections.emptyList());

		this.undertest = new SearchServlet(this.servletCommon, this.contentTree, this.contentServlet, this.db, this.dbCache, this.upnpService, this.rpcClient, this.thumbnailGenerator, this.searchEngine);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itDoesSimpleSearchFromParam() throws Exception {
		final ContentItem i0 = mockItem("thing 0", "foo");
		mockItem("thing 1", "bar");

		this.req.setParameter("query", "t=foo");
		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertThat(this.resp.getOutputAsString(), containsString("<h3>Local items: 1</h3>"));
		assertThat(this.resp.getOutputAsString(), containsString("<input type=\"text\" id=\"search\" name=\"query\" value=\"t&#61;foo\" "));
		assertPageContainsItem(i0, "", "?query&#61;t%3Dfoo&amp;offset&#61;0");
	}

	@Test
	public void itDoesSimpleSearchFromParamWithExtraQuery() throws Exception {
		this.req.setParameter("query", "t=foo OR t=bar");
		this.req.setParameter("extra_query", "f~^/foo/bar");
		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertThat(this.resp.getOutputAsString(), containsString("<input type=\"text\" id=\"search\" name=\"query\" value=\"(t&#61;foo OR t&#61;bar) f~^/foo/bar\" "));
	}

	@Test
	public void itDoesSimpleSearchFromPath() throws Exception {
		final ContentItem i0 = mockItem("thing 0", "foo");
		mockItem("thing 1", "bar");

		this.req.setPathInfo("/t=foo");
		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertThat(this.resp.getOutputAsString(), containsString("<h3>Local items: 1</h3>"));
		assertThat(this.resp.getOutputAsString(), containsString("<input type=\"text\" id=\"search\" name=\"query\" value=\"t&#61;foo\" "));
		assertPageContainsItem(i0, "../", "?query&#61;t%3Dfoo&amp;offset&#61;0");
	}

	@Test
	public void itServesSearchResultItems() throws Exception {
		final ContentItem i = mockItem("thing 0", "foo");

		final String pathInfo = "/search/t=foo/" + i.getId() + "." + i.getFormat().getExt();
		this.req.setRequestURI(pathInfo);
		this.req.setPathInfo(pathInfo);

		this.undertest.doGet(this.req, this.resp);

		verify(this.contentServlet).service(this.req, this.resp);
	}

	private void assertPageContainsItem(final ContentItem i, final String pathPrefix, final String itemQueryString) throws IOException {
		assertThat(this.resp.getOutputAsString(), containsString(
				"<li><a href=\"" + pathPrefix + "i/" + i.getId() + itemQueryString + "\" autofocus>"
						+ i.getFile().getName() + "</a>"
						+ " [<a href=\"" + pathPrefix + "c/" + i.getId() + "." + i.getFormat().getExt()
						+ "\" download=\"" + i.getFile().getName() + "\">" + i.getFileLength() + " B</a>]</li>"));
	}

	private ContentItem mockItem(final String name, final String... tags) throws Exception {
		final String id = this.mockMediaMetadataStore.addFileWithNameAndSuffexAndTags(name, ".jpeg", tags);
		final File file = new File(this.db.getFilePathForId(id));
		final ContentItem item = new ContentItem(id, "0", name, file, MediaFormat.JPEG);
		this.contentTree.addItem(item);
		return item;
	}

}

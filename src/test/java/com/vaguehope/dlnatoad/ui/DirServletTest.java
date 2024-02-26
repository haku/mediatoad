package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentServingHistory;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MockContent;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class DirServletTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MockContent mockContent;
	private DirServlet undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree, this.tmp);

		final ImageResizer imageResizer = new ImageResizer(this.tmp.getRoot());
		final ContentServingHistory contentServingHistory = new ContentServingHistory();
		final ServletCommon servletCommon = new ServletCommon(this.contentTree, imageResizer, "hostName", contentServingHistory, true);
		this.undertest = new DirServlet(servletCommon, this.contentTree);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itReturnsNodeAsHtmlPage() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode mockDir = mockDirs.get(0);
		final List<ContentItem> items = this.mockContent.givenMockItems(5, mockDir);

		this.req.setPathInfo("/" + mockDir.getId());
		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());

		final String page = this.resp.getContentAsString();
		System.out.println(page);
		assertThat(page, containsString("<title>dir 0 - DLNAtoad (hostName)</title>"));

		for (final ContentItem i : items) {
			assertThat(page, containsString("<li><a href=\"../c/" + i.getFile().getName() + "\">" + i.getFile().getName() + "</a></li>"));
		}
	}

	@Test
	public void itReturnsNodeAsAZipFile() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode mockDir = mockDirs.get(0);
		final List<ContentItem> items = this.mockContent.givenMockItems(5, mockDir);
		final List<String> expectedNames = items.stream().map((i) -> i.getFile().getName()).collect(Collectors.toList());

		this.req.setPathInfo("/" + mockDir.getId() + ".zip");
		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());

		final List<String> actualNames = new ArrayList<>();
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(this.resp.getContentAsByteArray()))) {
			ZipEntry e;
			while ((e = zis.getNextEntry()) != null) {
				actualNames.add(e.getName());
			}
		}

		assertEquals(expectedNames, actualNames);
	}

}

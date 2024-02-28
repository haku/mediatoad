package com.vaguehope.dlnatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.reflect.ReflectionObjectHandler;
import com.google.common.collect.ImmutableMap;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentServingHistory;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
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
		this.undertest = new DirServlet(servletCommon, this.contentTree, imageResizer, null);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itReturnsNodeAsHtmlPage() throws Exception {
		final List<ContentNode> mockDirs = this.mockContent.givenMockDirs(1);
		final ContentNode mockDir = mockDirs.get(0);
		final ContentNode subDir = this.mockContent.addMockDir("subdir", mockDir);
		final List<ContentItem> items = this.mockContent.givenMockItems(5, mockDir);
		final List<ContentItem> thumbItems = this.mockContent.givenMockItems(MediaFormat.JPEG, 3, mockDir);

		for (ContentItem i : items) {
			when(i.getFile().length()).thenReturn(10000L);
			i.reload();
			i.setDurationMillis(123456);
		}

		this.req.setPathInfo("/" + mockDir.getId());
		this.undertest.doGet(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());

		final String page = this.resp.getContentAsString();
		System.out.println(page);
		assertThat(page, containsString("<title>dir 0 - DLNAtoad (hostName)</title>"));

		assertThat(page, containsString(
				"<li><a href=\"../d/" + subDir.getId() + "\" autofocus>"
						+ subDir.getTitle() + "</a></li>"));

		for (ContentItem i : items) {
			assertThat(page, containsString(
					"<li><a href=\"../c/" + i.getId() + "." + i.getFormat().getExt() + "\">"
							+ i.getFile().getName() + "</a>"
							+ " [<a href=\"../c/" + i.getId() + "." + i.getFormat().getExt()
							+ "\" download=\"" + i.getId() + "." + i.getFormat().getExt() + "\">9.8 KB</a>]"
							+ " (00:02:03)</li>"));
		}

		for (ContentItem thumb : thumbItems) {
			assertThat(page, containsString(
					"<span class=\"thumbnail\">"
							+ "<a href=\"../i/" + thumb.getId() + "?node=" + mockDir.getId() + "\">"
							+ "<img src=\"../t/" + thumb.getId() + "\" title=\"" + thumb.getTitle() + "\">"
							+ "</a></span>"));
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

	/**
	 * mapScope:
	 * ReflectionObjectHandler: 10000 in 1800ms = 0.18ms per template
	 * SimpleObjectHandler:     10000 in 1400ms = 0.14ms per template
	 *
	 * classScope:
	 * ReflectionObjectHandler: 10000 in 1250ms = 0.13ms per template
	 * SimpleObjectHandler:     10000 in 1900ms = 0.19ms per template
	 *
	 * also classScope is 10x faster to build than mapScope.
	 */
	@Ignore
	@SuppressWarnings("resource")
	@Test
	public void itDoesAMicroBenckmark() throws Exception {
		final String template = "<ul>{{#list_items}}\n"
				+ "<li><a href=\"{{path_prefix}}{{path}}\">{{title}}</a></li>\n"
				+ "{{/list_items}}</ul>\n";

		final DefaultMustacheFactory mf = new DefaultMustacheFactory();

		// Choose 1 of these:
		mf.setObjectHandler(new ReflectionObjectHandler());
//		mf.setObjectHandler(new SimpleObjectHandler());

		final long start1 = System.nanoTime();
		final Map<String, Object> mapScope = new HashMap<>();
		final List<Map<String, String>> listItems = new ArrayList<>();
		mapScope.put("list_items", listItems);
		for (int i = 0; i < 500; i++) {
			listItems.add(ImmutableMap.of("path", "id" + i, "title", "title" + i));
		}
		final long duration1 = System.nanoTime() - start1;
		System.out.println("duration1: " + TimeUnit.NANOSECONDS.toNanos(duration1));

		final long start2 = System.nanoTime();
		final ViewClass classScope = new ViewClass();
		for (int i = 0; i < 500; i++) {
			final RowClass row = new RowClass("id" + i, "title" + i);
			classScope.list_items.add(row);
		}
		final long duration2 = System.nanoTime() - start2;
		System.out.println("duration2: " + TimeUnit.NANOSECONDS.toNanos(duration2));

		// Chose 1 of these:
//		final Object scope = mapScope;
		final Object scope = classScope;

		final Mustache mTemplate = mf.compile(new StringReader(template), "template");
		final CharArrayWriter output = new CharArrayWriter();
		mTemplate.execute(output, scope).flush();

		final long start3 = System.nanoTime();
		for (int i = 0; i < 10000; i++) {
			output.reset();
			mTemplate.execute(output, scope).flush();
		}
		final long duration3 = System.nanoTime() - start3;
		System.out.println("duration3: " + TimeUnit.NANOSECONDS.toMillis(duration3));
	}

	public static class ViewClass {
		public final List<RowClass> list_items = new ArrayList<>();
	}

	public static class RowClass {
		public final String path;
		public final String title;
		public RowClass(final String path, final String title) {
			this.path = path;
			this.title = title;
		}

	}

}

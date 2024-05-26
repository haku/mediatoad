package com.vaguehope.dlnatoad.ui;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.teleal.common.mock.http.MockHttpServletRequest;
import org.teleal.common.mock.http.MockHttpServletResponse;

import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.auth.Permission;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.InMemoryMediaDb;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.TagAutocompleter;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MockContent;

public class TagsServletTest {

	private ContentTree contentTree;
	private MockContent mockContent;
	private MediaDb mediaDb;
	private TagAutocompleter tagAutocompleter;

	private TagsServlet undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree();
		this.mockContent = new MockContent(this.contentTree);

		this.mediaDb = spy(new InMemoryMediaDb());
		this.tagAutocompleter = mock(TagAutocompleter.class);

		this.undertest = new TagsServlet(this.contentTree, this.mediaDb, this.tagAutocompleter);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itDoesBulkGetTags() throws Exception {
		final AuthList authlist = makeAuthList();
		final ContentNode dir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode(), authlist);
		final List<ContentItem> items = this.mockContent.givenMockItems(MediaFormat.JPEG, 2, dir);

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.addTag(items.get(0).getId(), "tag0", 0L);
			w.addTag(items.get(1).getId(), "tag0", 0L);
			w.addTag(items.get(0).getId(), "tag0", "foo", 0L);
			w.addTag(items.get(0).getId(), "tag1", "foo", 0L);
		}

		final String ids = "['" + items.stream().map(i -> i.getId()).collect(Collectors.joining("','")) + "']";
		final String body = "{\"action\": \"gettags\", \"ids\": " + ids + "}";

		this.req.setMethod("POST");
		this.req.setContent(body.getBytes());
		ReqAttr.USERNAME.set(this.req, "userfoo");

		this.undertest.service(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertEquals("["
				+ "{\"tag\":\"tag0\",\"cls\":\"\",\"count\":2},"
				+ "{\"tag\":\"tag0\",\"cls\":\"foo\",\"count\":1},"
				+ "{\"tag\":\"tag1\",\"cls\":\"foo\",\"count\":1}"
				+ "]", this.resp.getContentAsString());
	}

	@Test
	public void itDoesBulkAddTag() throws Exception {
		final ContentNode dir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode());
		final List<ContentItem> items = this.mockContent.givenMockItems(MediaFormat.JPEG, 2, dir);

		final String ids = "['" + items.stream().map(i -> i.getId()).collect(Collectors.joining("','")) + "']";
		final String body = "{\"action\": \"addtag\", \"tag\": \"mytag\", \"ids\": " + ids + "}";

		this.req.setMethod("POST");
		this.req.setContent(body.getBytes());
		ReqAttr.USERNAME.set(this.req, "userfoo");
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);

		this.undertest.service(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertEquals("[{\"tag\":\"mytag\",\"cls\":\"\",\"count\":2}]", this.resp.getContentAsString());
		verify(this.tagAutocompleter).changeTagCount("mytag", 2);
	}

	@Test
	public void itDoesBulkRmTag() throws Exception {
		final ContentNode dir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode());
		final List<ContentItem> items = this.mockContent.givenMockItems(MediaFormat.JPEG, 2, dir);

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.addTag(items.get(0).getId(), "mytag", 0L);
			w.addTag(items.get(1).getId(), "mytag", 0L);
			w.addTag(items.get(0).getId(), "othertag", 0L);
			w.addTag(items.get(1).getId(), "othertag", 0L);
		}

		final String ids = "['" + items.stream().map(i -> i.getId()).collect(Collectors.joining("','")) + "']";
		final String body = "{\"action\": \"rmtag\", \"tag\": \"mytag\", \"cls\": \"\", \"ids\": " + ids + "}";

		this.req.setMethod("POST");
		this.req.setContent(body.getBytes());
		ReqAttr.USERNAME.set(this.req, "userfoo");
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);

		this.undertest.service(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertEquals("[{\"tag\":\"othertag\",\"cls\":\"\",\"count\":2}]", this.resp.getContentAsString());
		verify(this.tagAutocompleter).changeTagCount("mytag", -2);
	}

	@Test
	public void itDoesBulkAddTagWithAuthList() throws Exception {
		final AuthList authlist = makeAuthListWithEdittags();
		final ContentNode dir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode(), authlist);
		final List<ContentItem> items = this.mockContent.givenMockItems(MediaFormat.JPEG, 2, dir);

		final String ids = "['" + items.stream().map(i -> i.getId()).collect(Collectors.joining("','")) + "']";
		final String body = "{\"action\": \"addtag\", \"tag\": \"mytag\", \"ids\": " + ids + "}";

		this.req.setMethod("POST");
		this.req.setContent(body.getBytes());
		ReqAttr.USERNAME.set(this.req, "userfoo");
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);

		this.undertest.service(this.req, this.resp);

		assertEquals(200, this.resp.getStatus());
		assertEquals("[{\"tag\":\"mytag\",\"cls\":\"\",\"count\":2}]", this.resp.getContentAsString());
		verify(this.tagAutocompleter).changeTagCount("mytag", 2);
	}

	@Test
	public void itBlocksUsersWithoutTagWriteAccess() throws Exception {
		final ContentNode dir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode());
		final List<ContentItem> items = this.mockContent.givenMockItems(MediaFormat.JPEG, 2, dir);

		final String ids = "['" + items.stream().map(i -> i.getId()).collect(Collectors.joining("','")) + "']";
		final String body = "{\"action\": \"addtag\", \"tag\": \"mytag\", \"ids\": " + ids + "}";

		this.req.setMethod("POST");
		this.req.setContent(body.getBytes());
		ReqAttr.USERNAME.set(this.req, "userfoo");

		this.undertest.service(this.req, this.resp);
		assertEquals(403, this.resp.getStatus());
		assertEquals("Forbidden\n", this.resp.getContentAsString());
	}

	@Test
	public void itBlocksIncompleteWriteAccess() throws Exception {
		final AuthList writeAuth = makeAuthListWithEdittags();
		final ContentNode writeDir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode(), writeAuth);
		final ContentItem writeI = this.mockContent.givenMockItems(MediaFormat.JPEG, 1, writeDir).get(0);

		final AuthList roAuth = makeAuthList();
		final ContentNode roDir = this.mockContent.addMockDir("dir", this.contentTree.getRootNode(), roAuth);
		final ContentItem roI = this.mockContent.givenMockItems(MediaFormat.JPEG, 1, roDir).get(0);

		final String ids = "['" + Arrays.asList(writeI, roI).stream().map(i -> i.getId()).collect(Collectors.joining("','")) + "']";
		final String body = "{\"action\": \"addtag\", \"tag\": \"mytag\", \"ids\": " + ids + "}";

		this.req.setMethod("POST");
		this.req.setContent(body.getBytes());
		ReqAttr.USERNAME.set(this.req, "userfoo");
		ReqAttr.ALLOW_EDIT_TAGS.set(this.req, Boolean.TRUE);

		this.undertest.service(this.req, this.resp);
		assertEquals(403, this.resp.getStatus());
		assertEquals("Forbidden\n", this.resp.getContentAsString());
	}

	private static AuthList makeAuthList() {
		final AuthList authlist = mock(AuthList.class);
		when(authlist.hasUser("userfoo")).thenReturn(true);
		return authlist;
	}

	private static AuthList makeAuthListWithEdittags() {
		final AuthList authlist = makeAuthList();
		when(authlist.hasUserWithPermission("userfoo", Permission.EDITTAGS)).thenReturn(true);
		return authlist;
	}

}

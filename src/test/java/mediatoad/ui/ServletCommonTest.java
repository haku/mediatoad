package mediatoad.ui;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Test;

import faecommon.servlet.MockHttpServletRequest;
import faecommon.servlet.MockHttpServletResponse;
import mediatoad.FakeServletCommon;
import mediatoad.auth.FakeAuth;
import mediatoad.auth.ReqAttr;
import mediatoad.auth.Users.User;
import mediatoad.media.ContentItem;
import mediatoad.media.ContentNode;
import mediatoad.media.ContentTree;

public class ServletCommonTest {

	@Test
	public void itExtractsIdFromPath() throws Exception {
		ServletCommon sc = FakeServletCommon.makeWithPathPrefix("dlnatoad");

		assertEquals("0", sc.idFromPath("", "0"));
		assertEquals("0", sc.idFromPath("/", "0"));
		assertEquals("0", sc.idFromPath("/dlnatoad", "0"));
		assertEquals("0", sc.idFromPath("/dlnatoad/", "0"));

		assertEquals("123", sc.idFromPath("/c/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/c/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/c/123.ext", "0"));

		assertEquals("123", sc.idFromPath("/t/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/t/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/t/123.ext", "0"));

		// Not sure it should do this but it does.
		assertEquals("123", sc.idFromPath("/foo/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/foo/123", "0"));
		assertEquals("123", sc.idFromPath("/dlnatoad/foo/123.ext", "0"));

		sc = FakeServletCommon.makeWithPathPrefix("mediatoad");

		assertEquals("123", sc.idFromPath("/mediatoad/c/123", "0"));
		assertEquals("123", sc.idFromPath("/mediatoad/c/123.ext", "0"));
		assertEquals("123", sc.idFromPath("/mediatoad/t/123", "0"));
		assertEquals("123", sc.idFromPath("/mediatoad/t/123.ext", "0"));
	}

	@Test
	public void itExtractsFirstDirFromPath() throws Exception {
		ServletCommon sc = FakeServletCommon.makeWithPathPrefix("dlnatoad");

		assertEquals(null, sc.firstDirFromPath(null));
		assertEquals(null, sc.firstDirFromPath(""));
		assertEquals(null, sc.firstDirFromPath("0"));
		assertEquals(null, sc.firstDirFromPath("/"));
		assertEquals(null, sc.firstDirFromPath("/0"));

		assertEquals("0", sc.firstDirFromPath("/0/"));
		assertEquals("0", sc.firstDirFromPath("/0/foo.ext"));
		assertEquals("abc", sc.firstDirFromPath("/abc/foo.ext"));

		assertEquals(null, sc.firstDirFromPath("/dlnatoad/0"));
		assertEquals("0", sc.firstDirFromPath("/dlnatoad/0/"));
		assertEquals("0", sc.firstDirFromPath("/dlnatoad/0/foo.ext"));
		assertEquals("abc", sc.firstDirFromPath("/dlnatoad/abc/foo.ext"));

		sc = FakeServletCommon.makeWithPathPrefix("mediatoad");

		assertEquals(null, sc.firstDirFromPath("/mediatoad/0"));
		assertEquals("0", sc.firstDirFromPath("/mediatoad/0/"));
		assertEquals("0", sc.firstDirFromPath("/mediatoad/0/foo.ext"));
		assertEquals("abc", sc.firstDirFromPath("/mediatoad/abc/foo.ext"));
	}

	@Test
	public void itExtractsFileFromPath() throws Exception {
		ServletCommon sc = FakeServletCommon.makeWithPathPrefix("dlnatoad");

		assertEquals(null, sc.fileFromPath(null));
		assertEquals(null, sc.fileFromPath(""));
		assertEquals(null, sc.fileFromPath("0"));
		assertEquals(null, sc.fileFromPath("/"));

		assertEquals("0", sc.fileFromPath("/0"));
		assertEquals("0", sc.fileFromPath("/dlnatoad/0"));
		assertEquals("t=tag", sc.fileFromPath("/t=tag"));
		assertEquals("t=tag", sc.fileFromPath("/dlnatoad/t=tag"));

		sc = FakeServletCommon.makeWithPathPrefix("mediatoad");

		assertEquals("0", sc.fileFromPath("/mediatoad/0"));
		assertEquals("t=tag", sc.fileFromPath("/mediatoad/t=tag"));
	}

	@Test
	public void itResolvesItemFromPathWithPermissionsChecks() throws Exception {
		final ContentTree contentTree = mock(ContentTree.class);
		final ServletCommon sc = FakeServletCommon.make(contentTree);

		final MockHttpServletRequest req = new MockHttpServletRequest();
		checkGetItemResp(sc, req, 400, "ID missing.\n");

		req.setPathInfo("/thing/myitemid");
		checkGetItemResp(sc, req, 404, "Not found.\n");

		final ContentItem item = mock(ContentItem.class);
		when(item.getParentId()).thenReturn("parentid");
		when(contentTree.getItem("myitemid")).thenReturn(item);
		checkGetItemResp(sc, req, 500, "Parent ID is invalid.\n");

		final ContentNode node = mock(ContentNode.class);
		when(contentTree.getNode("parentid")).thenReturn(node);
		checkGetItemResp(sc, req, 401, "Unauthorized\n");

		final User user = FakeAuth.user();
		ReqAttr.USER.set(req, user);
		checkGetItemResp(sc, req, 403, "Forbidden\n");

		when(node.isUserAuth(user)).thenReturn(true);
		checkGetItemResp(sc, req, 200, "");

		when(node.isUserAuth(user)).thenReturn(false);
		checkGetItemResp(sc, req, 403, "Forbidden\n");

		when(item.isUserAuth(user)).thenReturn(true);
		checkGetItemResp(sc, req, 200, "");
	}

	private static void checkGetItemResp(final ServletCommon sc, final MockHttpServletRequest req, final int status, final String body) throws IOException {
		final MockHttpServletResponse resp = new MockHttpServletResponse();
		sc.itemFromPathWithPermissionOrError(req, resp);
		assertEquals(body, resp.getOutputAsString());
		assertEquals(status, resp.getStatus());
	}

}

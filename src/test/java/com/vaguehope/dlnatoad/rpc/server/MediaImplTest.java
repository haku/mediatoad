package com.vaguehope.dlnatoad.rpc.server;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.vaguehope.dlnatoad.MetricAssert;
import com.vaguehope.dlnatoad.auth.AuthList;
import com.vaguehope.dlnatoad.auth.Permission;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.MockMediaMetadataStore;
import com.vaguehope.dlnatoad.db.Playback;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MockContent;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ExcludedChange;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ListNodeReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ListNodeRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.MediaItem;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.MediaNode;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.MediaTag;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.Range;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.RecordPlaybackReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.RecordPlaybackRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.TagAction;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.TagChange;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.UpdateExcludedReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.UpdateExcludedRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.UpdateTagsReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.UpdateTagsRequest;

import io.grpc.Context;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.prometheus.metrics.model.snapshots.Labels;

public class MediaImplTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MediaDb mediaDb;
	private WritableMediaDb writableMediaDb;
	private MediaImpl undertest;
	private MetricAssert metricAssert;

	@SuppressWarnings("resource")
	@Before
	public void before() throws Exception {
		this.contentTree = new ContentTree(false);
		this.mediaDb = mock(MediaDb.class);
		this.writableMediaDb = mock(WritableMediaDb.class);
		when(this.mediaDb.getWritable()).thenReturn(this.writableMediaDb);
		this.undertest = new MediaImpl(this.contentTree, this.mediaDb);
		this.metricAssert = new MetricAssert();
	}

	private MockContent setupFakeContent() {
		return new MockContent(this.contentTree, this.tmp);
	}

	private MockMediaMetadataStore setupFakeDb() throws SQLException {
		final MockMediaMetadataStore mockMediaMetadataStore = MockMediaMetadataStore.withMockExSvc(this.tmp);
		this.mediaDb = mockMediaMetadataStore.getMediaDb();
		this.undertest = new MediaImpl(this.contentTree, this.mediaDb);
		return mockMediaMetadataStore;
	}

	// TODO test other methods!

	@Test
	public void itListsNode() throws Exception {
		final ContentItem item = mockItem("itemid", "parent");
		when(this.mediaDb.getTags("itemid", false, false)).thenReturn(asList(new Tag("foo", "cls", 1234567890L, false)));
		when(this.mediaDb.getPlayback(Collections.singletonList("itemid"))).thenReturn(ImmutableMap.of("itemid", new Playback(0, 0, 0, true)));

		final ContentNode node = mockNode("nodeid");
		when(node.getCopyOfItems()).thenReturn(Collections.singletonList(item));

		final StreamObserver<ListNodeReply> respObs = mock(ServerCallStreamObserver.class);
		this.undertest.listNode(ListNodeRequest.newBuilder().setNodeId("nodeid").build(), respObs);

		verify(respObs).onNext(ListNodeReply.newBuilder()
				.setNode(MediaNode.newBuilder()
						.setId("nodeid")
						.setTitle("title for nodeid")
						.setParentId("parent-for-nodeid")
						.build())
				.addItem(MediaItem.newBuilder()
						.setId("itemid")
						.setTitle("title for itemid")
						.setMimeType("image/jpeg")
						.setExcluded(true)
						.addTag(MediaTag.newBuilder().setTag("foo").setCls("cls").setModifiedMillis(1234567890L).build())
						.build())
				.build());
		verify(respObs).onCompleted();
	}

	@Test
	public void itSearches() throws Exception {
		final MockMediaMetadataStore mockMediaMetadataStore = setupFakeDb();
		final long time = mockMediaMetadataStore.getNowMillis();

		final String name = "thing 0";
		final String tag = "foo";
		final String id = mockMediaMetadataStore.addFileWithNameAndSuffexAndTags(name, ".mp3", tag);
		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.setFileExcluded(id, true, true);
			w.addTag(id, "other", "class1", time);
		}
		final File file = new File(this.mediaDb.getFilePathForId(id));
		this.contentTree.addItem(new ContentItem(id, "0", name, file, MediaFormat.MP3));

		final StreamObserver<SearchReply> respObs = mock(ServerCallStreamObserver.class);
		this.undertest.search(SearchRequest.newBuilder().setQuery("t=foo").build(), respObs);

		verify(respObs).onNext(SearchReply.newBuilder()
				.addResult(MediaItem.newBuilder()
						.setId(id)
						.setTitle(name)
						.setMimeType("audio/mpeg")
						.setFileLength(file.length())
						.setExcluded(true)
						.addTag(MediaTag.newBuilder()
								.setTag("foo")
								.setModifiedMillis(time)
								.build())
						.addTag(MediaTag.newBuilder()
								.setTag("other")
								.setCls("class1")
								.setModifiedMillis(time)
								.build())
						.build())
				.build());
		verify(respObs).onCompleted();
	}

	@Test
	public void itServesContent() throws Exception {
		final ReadMediaRequest req = ReadMediaRequest.newBuilder().setId("someid").build();
		final byte[] data = mockItemFileData("someid", "parentid", 1000 * 1024);

		final ServerCallStreamObserver<ReadMediaReply> respObs = mock(ServerCallStreamObserver.class);
		when(respObs.isReady()).thenReturn(true);
		this.undertest.readMedia(req, respObs);

		verify(respObs, Mockito.never()).onError(any(Throwable.class));
		final List<ReadMediaReply> msgs = getRespMsgs(4, respObs);
		assertEquals(1000 * 1024, msgs.get(0).getTotalFileLength());
		assertEquals("image/jpeg", msgs.get(0).getMimeType());
		assertFalse(msgs.get(0).hasRangeIndex());
		assertArrayEquals(data, joinContent(msgs).toByteArray());

		this.metricAssert.assertCounter("rpc_chunks_sent", Labels.of(), 4);
	}

	@Test
	public void itHandlesSmallStartRangeRequest() throws Exception {
		testRangeRequest(0, 99, 1000, 1);
	}

	@Test
	public void itHandlesSmallMiddleRangeRequest() throws Exception {
		testRangeRequest(200, 499, 1000, 1);
	}

	@Test
	public void itHandlesSmallEndRangeRequest() throws Exception {
		testRangeRequest(500, 999, 1000, 1);
	}

	@Test
	public void itHandlesBigStartRangeRequest() throws Exception {
		testRangeRequest(0, (int) (2.5 * 256 * 1024), 1000 * 1024, 3);
	}

	@Test
	public void itHandlesBigMiddleRangeRequest() throws Exception {
		testRangeRequest((int) (0.3 * 256 * 1024), 3 * 256 * 1024, 1000 * 1024, 3);
	}

	@Test
	public void itHandlesBigEndRangeRequest() throws Exception {
		testRangeRequest((int) (1.5 * 256 * 1024), (1024 * 1024) - 1, 1024 * 1024, 3);
	}

	private void testRangeRequest(final int first, final int last, final int fileLength, final int expectedChunkCount) throws IOException {
		final ReadMediaRequest req = ReadMediaRequest.newBuilder()
				.setId("someid")
				.addRange(Range.newBuilder().setFirst(first).setLast(last).build())
				.build();
		final byte[] data = mockItemFileData("someid", "parentid", fileLength);

		final ServerCallStreamObserver<ReadMediaReply> respObs = mock(ServerCallStreamObserver.class);
		when(respObs.isReady()).thenReturn(true);
		this.undertest.readMedia(req, respObs);

		verify(respObs, Mockito.never()).onError(any(Throwable.class));
		final byte[] expected = new byte[last - first + 1];
		System.arraycopy(data, first, expected, 0, expected.length);
		final List<ReadMediaReply> msgs = getRespMsgs(expectedChunkCount, respObs);
		assertEquals(fileLength, msgs.get(0).getTotalFileLength());
		assertEquals("image/jpeg", msgs.get(0).getMimeType());
		assertEquals(0, msgs.get(0).getRangeIndex());
		assertArrayEquals(expected, joinContent(msgs).toByteArray());
	}

	@SuppressWarnings("resource")
	@Test
	public void itRecordsPlayback() throws Exception {
		mockItemFileData("someid", "parent", 1);
		final long time = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);
		final RecordPlaybackRequest req = RecordPlaybackRequest.newBuilder()
				.setId("someid")
				.setStartTimeMillis(time)
				.setCompleted(true)
				.build();

		final StreamObserver<RecordPlaybackReply> respObs = mock(StreamObserver.class);
		this.undertest.recordPlayback(req, respObs);

		final InOrder ord = inOrder(this.writableMediaDb);
		ord.verify(this.writableMediaDb).recordPlayback("someid", time, true);
		ord.verify(this.writableMediaDb).close();
		verify(respObs).onNext(RecordPlaybackReply.newBuilder().build());
		verify(respObs).onCompleted();

		this.undertest.recordPlayback(req, respObs);
		ord.verifyNoMoreInteractions();
	}

	@SuppressWarnings({ "resource", "unchecked" })
	@Test
	public void itChecksAuthWhenRecordingPlayback() throws Exception {
		final ContentNode node = mockNode("parent");
		mockItem("someid", "parent");

		final long time = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);
		final RecordPlaybackRequest req = RecordPlaybackRequest.newBuilder()
				.setId("someid")
				.setStartTimeMillis(time)
				.setCompleted(true)
				.build();

		final StreamObserver<RecordPlaybackReply> respObs = mock(StreamObserver.class);

		when(node.isUserAuth(nullable(String.class))).thenReturn(false);
		this.undertest.recordPlayback(req, respObs);
		verifyErrorStatus(respObs, Code.NOT_FOUND);
		verifyNoInteractions(this.writableMediaDb);

		reset(respObs);
		final Context ctx = Context.current().withValue(JwtInterceptor.USERNAME_CONTEXT_KEY, "someone");
		ctx.run(() -> this.undertest.recordPlayback(req, respObs));
		verifyErrorStatus(respObs, Code.NOT_FOUND);
		verifyNoInteractions(this.writableMediaDb);

		when(node.isUserAuth("someone")).thenReturn(true);
		ctx.run(() -> this.undertest.recordPlayback(req, respObs));
		verify(this.writableMediaDb).recordPlayback("someid", time, true);

		verify(respObs).onNext(RecordPlaybackReply.newBuilder().build());
		verify(respObs).onCompleted();
	}

	@Test
	public void itUpdatesTags() throws Exception {
		final MockContent mockContent = setupFakeContent();
		final MockMediaMetadataStore mockMediaMetadataStore = setupFakeDb();

		final Context ctx = Context.current().withValues(
				JwtInterceptor.USERNAME_CONTEXT_KEY, "testuser@client",
				JwtInterceptor.PERMISSIONS_CONTEXT_KEY, ImmutableSet.of(Permission.EDITTAGS));
		final AuthList authList = AuthList.ofNameAndPermission("testuser@client", Permission.EDITTAGS);
		final ContentNode node = mockContent.addMockDir("some-parent-node", authList);

		final String id = mockMediaMetadataStore.addFileWithNameAndSuffexAndTags("thing 0", ".mp3", "existing");
		mockContent.addMockItem(MediaFormat.MP3, id, node, null);

		final UpdateTagsRequest req = UpdateTagsRequest.newBuilder()
				.addChange(TagChange.newBuilder()
						.setId(id)
						.setAction(TagAction.ADD)
						.addTag(MediaTag.newBuilder().setTag("foo00").setCls("bar00").build())
						.build())
				.addChange(TagChange.newBuilder()
						.setId(id)
						.setAction(TagAction.REMOVE)
						.addTag(MediaTag.newBuilder().setTag("existing").setCls("").build())
						.build())
				.build();

		final StreamObserver<UpdateTagsReply> respObs = mock(StreamObserver.class);
		ctx.run(() -> this.undertest.updateTags(req, respObs));
		verify(respObs).onNext(UpdateTagsReply.newBuilder().build());
		verify(respObs).onCompleted();

		final Collection<Tag> actualAll = this.mediaDb.getTags(id, false, false);
		assertThat(actualAll, hasSize(1));
		final Tag actualTag = actualAll.iterator().next();
		assertEquals("foo00", actualTag.getTag());
		assertEquals("bar00", actualTag.getCls());
	}

	@Test
	public void itUpdatesExcluded() throws Exception {
		final MockContent mockContent = setupFakeContent();
		final MockMediaMetadataStore mockMediaMetadataStore = setupFakeDb();

		final Context ctx = Context.current().withValues(
				JwtInterceptor.USERNAME_CONTEXT_KEY, "testuser@client",
				JwtInterceptor.PERMISSIONS_CONTEXT_KEY, ImmutableSet.of(Permission.EDITTAGS));
		final AuthList authList = AuthList.ofNameAndPermission("testuser@client", Permission.EDITTAGS);
		final ContentNode node = mockContent.addMockDir("some-parent-node", authList);

		final String id1 = mockMediaMetadataStore.addFileWithName("thing 1");
		final String id2 = mockMediaMetadataStore.addFileWithName("thing 2");
		mockContent.addMockItem(id1, node);
		mockContent.addMockItem(id2, node);

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.setFileExcluded(id2, true, true);
		}

		final UpdateExcludedRequest req = UpdateExcludedRequest.newBuilder()
				.addChange(ExcludedChange.newBuilder()
						.setId(id1)
						.setExcluded(true)
						.build())
				.addChange(ExcludedChange.newBuilder()
						.setId(id2)
						.setExcluded(false)
						.build())
				.build();

		final StreamObserver<UpdateExcludedReply> respObs = mock(StreamObserver.class);
		ctx.run(() -> this.undertest.updateExcluded(req, respObs));
		verify(respObs).onNext(UpdateExcludedReply.newBuilder().build());
		verify(respObs).onCompleted();

		assertEquals(true, this.mediaDb.getPlayback(id1).isExcluded());
		assertEquals(false, this.mediaDb.getPlayback(id2).isExcluded());
	}

	private static void verifyErrorStatus(final StreamObserver<?> observer, final Code code) {
		final ArgumentCaptor<StatusRuntimeException> cap = ArgumentCaptor.forClass(StatusRuntimeException.class);
		final InOrder ord = inOrder(observer);
		ord.verify(observer).onError(cap.capture());
		assertEquals(code, cap.getValue().getStatus().getCode());
		ord.verifyNoMoreInteractions();
	}

	private byte[] mockItemFileData(final String id, final String parentId, final int length) throws IOException {
		final ContentItem item = mockItem(id, parentId);
		mockNode(parentId);
		return makeFile(item, length);
	}

	private ContentNode mockNode(final String id) {
		final ContentNode node = mock(ContentNode.class);
		when(node.getId()).thenReturn(id);
		when(node.getTitle()).thenReturn("title for " + id);
		when(node.getParentId()).thenReturn("parent-for-" + id);
		when(node.isUserAuth(nullable(String.class))).thenReturn(true);
		this.contentTree.addNode(node);
		return node;
	}

	private ContentItem mockItem(final String id, final String parentId) {
		final ContentItem item = mock(ContentItem.class);
		when(item.getId()).thenReturn(id);
		when(item.getParentId()).thenReturn(parentId);
		when(item.getTitle()).thenReturn("title for " + id);
		when(item.getFormat()).thenReturn(MediaFormat.JPEG);
		this.contentTree.addItem(item);
		return item;
	}

	private byte[] makeFile(final ContentItem item, final int length) throws IOException {
		final File file = this.tmp.newFile();
		final byte[] data = new byte[length];
		fillArray(data);
		FileUtils.writeByteArrayToFile(file, data);
		when(item.getFile()).thenReturn(file);
		return data;
	}

	private static void fillArray(final byte[] arr) {
		final Random rnd = new Random();
		for (int i = 0; i < arr.length; i++) {
			arr[i] = (byte) rnd.nextInt();
		}
	}

	private static List<ReadMediaReply> getRespMsgs(final int expectedChunks, final StreamObserver<ReadMediaReply> respObs) {
		final ArgumentCaptor<ReadMediaReply> cap = ArgumentCaptor.forClass(ReadMediaReply.class);
		verify(respObs, times(expectedChunks)).onNext(cap.capture());
		return cap.getAllValues();
	}

	private static ByteArrayOutputStream joinContent(final List<ReadMediaReply> msgs) throws IOException {
		final ByteArrayOutputStream actual = new ByteArrayOutputStream();
		for (final ReadMediaReply r : msgs) {
			r.getContent().writeTo(actual);
		}
		return actual;
	}

}

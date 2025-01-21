package com.vaguehope.dlnatoad.rpc.server;

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

import com.vaguehope.dlnatoad.MetricAssert;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.MockMediaMetadataStore;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.MediaItem;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.MediaTag;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.Range;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.RecordPlaybackReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.RecordPlaybackRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchRequest;

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
		this.contentTree = mock(ContentTree.class);
		this.mediaDb = mock(MediaDb.class);
		this.writableMediaDb = mock(WritableMediaDb.class);
		when(this.mediaDb.getWritable()).thenReturn(this.writableMediaDb);
		this.undertest = new MediaImpl(this.contentTree, this.mediaDb);
		this.metricAssert = new MetricAssert();
	}

	// TODO test other methods!

	@Test
	public void itSearches() throws Exception {
		final MockMediaMetadataStore mockMediaMetadataStore = MockMediaMetadataStore.withMockExSvc(this.tmp);
		this.mediaDb = mockMediaMetadataStore.getMediaDb();
		this.contentTree = new ContentTree();
		this.undertest = new MediaImpl(this.contentTree, this.mediaDb);

		final String name = "thing 0";
		final String tag = "foo";
		final String id = mockMediaMetadataStore.addFileWithNameAndSuffexAndTags(name, ".mp3", tag);
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
						.addTag(MediaTag.newBuilder()
								.setTag("foo")
								.setModifiedMillis(mockMediaMetadataStore.getNowMillis())
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
		when(node.isUserAuth(nullable(String.class))).thenReturn(true);
		when(this.contentTree.getNode(id)).thenReturn(node);
		return node;
	}

	private ContentItem mockItem(final String id, final String parentId) {
		final ContentItem item = mock(ContentItem.class);
		when(item.getId()).thenReturn(id);
		when(item.getParentId()).thenReturn(parentId);
		when(item.getFormat()).thenReturn(MediaFormat.JPEG);
		when(this.contentTree.getItem(id)).thenReturn(item);
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

package com.vaguehope.dlnatoad.rpc.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.Range;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaRequest;

import io.grpc.stub.StreamObserver;

public class MediaImplTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private ContentTree contentTree;
	private MediaImpl undertest;

	@Before
	public void before() throws Exception {
		this.contentTree = mock(ContentTree.class);
		this.undertest = new MediaImpl(this.contentTree, null);
	}

	// TODO test other methods!

	@Test
	public void itServesContent() throws Exception {
		final ReadMediaRequest req = ReadMediaRequest.newBuilder().setId("someid").build();
		final byte[] data = mockItemFileData("someid", "parentid", 1000 * 1024);

		final StreamObserver<ReadMediaReply> respObs = mock(StreamObserver.class);
		this.undertest.readMedia(req, respObs);

		verify(respObs, Mockito.never()).onError(any(Throwable.class));
		final List<ReadMediaReply> msgs = getRespMsgs(4, respObs);
		assertEquals(1000 * 1024, msgs.get(0).getTotalFileLength());
		assertEquals("image/jpeg", msgs.get(0).getMimeType());
		assertFalse(msgs.get(0).hasRangeIndex());
		assertArrayEquals(data, joinContent(msgs).toByteArray());
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

	private void testRangeRequest(int first, int last, int fileLength, int expectedChunkCount) throws IOException {
		final ReadMediaRequest req = ReadMediaRequest.newBuilder()
				.setId("someid")
				.addRange(Range.newBuilder().setFirst(first).setLast(last).build())
				.build();
		final byte[] data = mockItemFileData("someid", "parentid", fileLength);

		final StreamObserver<ReadMediaReply> respObs = mock(StreamObserver.class);
		this.undertest.readMedia(req, respObs);

		verify(respObs, Mockito.never()).onError(any(Throwable.class));
		byte[] expected = new byte[last - first + 1];
		System.arraycopy(data, first, expected, 0, expected.length);
		final List<ReadMediaReply> msgs = getRespMsgs(expectedChunkCount, respObs);
		assertEquals(fileLength, msgs.get(0).getTotalFileLength());
		assertEquals("image/jpeg", msgs.get(0).getMimeType());
		assertEquals(0, msgs.get(0).getRangeIndex());
		assertArrayEquals(expected, joinContent(msgs).toByteArray());
	}

	private byte[] mockItemFileData(String id, String parentId, int length) throws IOException {
		final ContentItem item = mockItem(id, parentId);
		mockNode(parentId);
		return makeFile(item, length);
	}

	private void mockNode(String id) {
		final ContentNode node = mock(ContentNode.class);
		when(this.contentTree.getNode(id)).thenReturn(node);
	}

	private ContentItem mockItem(String id, String parentId) {
		final ContentItem item = mock(ContentItem.class);
		when(item.getId()).thenReturn(id);
		when(item.getParentId()).thenReturn(parentId);
		when(item.getFormat()).thenReturn(MediaFormat.JPEG);
		when(this.contentTree.getItem(id)).thenReturn(item);
		return item;
	}

	private byte[] makeFile(final ContentItem item, int length) throws IOException {
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

	private static List<ReadMediaReply> getRespMsgs(int expectedChunks, final StreamObserver<ReadMediaReply> respObs) {
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

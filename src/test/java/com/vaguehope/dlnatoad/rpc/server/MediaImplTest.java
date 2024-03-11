package com.vaguehope.dlnatoad.rpc.server;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

	@Test
	public void itServesContent() throws Exception {
		final ReadMediaRequest req = ReadMediaRequest.newBuilder().setId("someid").build();
		final StreamObserver<ReadMediaReply> respObs = mock(StreamObserver.class);

		final ContentItem item = mock(ContentItem.class);
		when(item.getParentId()).thenReturn("parentid");
		when(item.getFormat()).thenReturn(MediaFormat.JPEG);
		when(this.contentTree.getItem("someid")).thenReturn(item);

		final ContentNode node = mock(ContentNode.class);
		when(this.contentTree.getNode("parentid")).thenReturn(node);

		final File file = this.tmp.newFile();
		final byte[] data = new byte[1000 * 1024];
		fillArray(data);
		FileUtils.writeByteArrayToFile(file, data);
		when(item.getFile()).thenReturn(file);

		this.undertest.readMedia(req, respObs);
		verify(respObs, Mockito.never()).onError(any(Throwable.class));

		final ArgumentCaptor<ReadMediaReply> cap = ArgumentCaptor.forClass(ReadMediaReply.class);
		verify(respObs, times(4)).onNext(cap.capture());

		final ByteArrayOutputStream actual = new ByteArrayOutputStream();
		for (final ReadMediaReply r : cap.getAllValues()) {
			r.getContent().writeTo(actual);
		}
		assertArrayEquals(data, actual.toByteArray());
	}

	private static void fillArray(final byte[] arr) {
		final Random rnd = new Random();
		for (int i = 0; i < arr.length; i++) {
			arr[i] = (byte) rnd.nextInt();
		}
	}

}

package com.vaguehope.dlnatoad.rpc.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.google.protobuf.ByteString;
import com.vaguehope.dlnatoad.MockHttpServletRequest;
import com.vaguehope.dlnatoad.MockHttpServletResponse;
import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaBlockingStub;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaRequest;

public class RemoteContentServletTest {

	private RpcClient rpcClient;
	private RemoteContentServlet undertest;

	private MockHttpServletRequest req;
	private MockHttpServletResponse resp;

	@Before
	public void before() throws Exception {
		this.rpcClient = mock(RpcClient.class);
		this.undertest = new RemoteContentServlet(this.rpcClient);

		this.req = new MockHttpServletRequest();
		this.resp = new MockHttpServletResponse();
	}

	@Test
	public void itDoesSomething() throws Exception {
		final MediaBlockingStub stub = mock(MediaBlockingStub.class);
		when(this.rpcClient.getMediaBlockingStub("1")).thenReturn(stub);

		final Iterator<ReadMediaReply> replies = Arrays.asList(
				ReadMediaReply.newBuilder().setTotalFileLength(9).setMimeType("image/jpeg").setContent(ByteString.copyFrom("123", StandardCharsets.UTF_8)).build(),
				ReadMediaReply.newBuilder().setContent(ByteString.copyFrom("456", StandardCharsets.UTF_8)).build(),
				ReadMediaReply.newBuilder().setContent(ByteString.copyFrom("789", StandardCharsets.UTF_8)).build()).iterator();
		when(stub.readMedia(isA(ReadMediaRequest.class))).thenReturn(replies);
		when(stub.withDeadlineAfter(anyLong(), isA(TimeUnit.class))).thenReturn(stub);

		this.req.setPathInfo("/1/someid.jpeg");
		this.undertest.doGet(this.req, this.resp);

		verify(stub).readMedia(ReadMediaRequest.newBuilder().setId("someid").build());
		assertEquals("123456789", this.resp.getOutputAsString());
		assertEquals(9, this.resp.getContentLength());
		assertEquals("image/jpeg", this.resp.getContentType());
	}

}

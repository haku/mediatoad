package com.vaguehope.dlnatoad.rpc;

import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaBlockingStub;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchRequest;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

public class ApiTest {

	private static final Metadata.Key<String> CUSTOM_HEADER_KEY = Metadata.Key.of("custom_client_header_key", Metadata.ASCII_STRING_MARSHALLER);

	@Ignore
	@Test
	public void itCallsAnApi() throws Exception {
		final String target = "localhost:8192";
		final ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build(); // Long-lived, reusable.
		try {
			final Metadata headers = new Metadata();
			headers.put(CUSTOM_HEADER_KEY, "my value");
			final ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(headers);
			final Channel newChannel = ClientInterceptors.intercept(channel, interceptor);

			final MediaBlockingStub blockingStub = MediaGrpc.newBlockingStub(newChannel); // Reusable.
			final SearchRequest request = SearchRequest.newBuilder().setQuery("t=foo").build();
			final SearchReply response = blockingStub.search(request);
			System.out.println(response);
		}
		finally {
			channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}
	}

}

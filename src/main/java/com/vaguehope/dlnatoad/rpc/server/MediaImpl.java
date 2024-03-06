package com.vaguehope.dlnatoad.rpc.server;

import com.vaguehope.dlnatoad.rpc.MediaGrpc;
import com.vaguehope.dlnatoad.rpc.MediaToadProto;
import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaImplBase;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.MediaItem;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchRequest;

import io.grpc.stub.StreamObserver;

public class MediaImpl extends MediaGrpc.MediaImplBase {

	@Override
	public void search(final SearchRequest request, final StreamObserver<SearchReply> responseObserver) {
		final MediaItem mockItem = MediaItem.newBuilder()
				.setId("null")
				.setTitle("Mock result")
				.build();
		final SearchReply ret = SearchReply.newBuilder().addResult(mockItem).build();
		responseObserver.onNext(ret);
		responseObserver.onCompleted();
	}

}

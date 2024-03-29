package com.vaguehope.dlnatoad.rpc.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import com.google.protobuf.ByteString;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;
import com.vaguehope.dlnatoad.db.search.SortOrder;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.rpc.MediaGrpc;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.MediaItem;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ReadMediaRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchReply.Builder;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchRequest;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class MediaImpl extends MediaGrpc.MediaImplBase {

	private static final int MAX_SEARCH_RESULTS = 500;
	private static final int MESSAGE_SIZE_BYTES = 256 * 1024;

	private final ContentTree contentTree;
	private final MediaDb mediaDb;

	public MediaImpl(final ContentTree contentTree, final MediaDb mediaDb) {
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
	}

	@Override
	public void search(final SearchRequest request, final StreamObserver<SearchReply> responseObserver) {
		// TODO figure out auth.
		final String username = null;
		final Set<BigInteger> authIds = null;
		final Integer offset = 0;
		final Integer limit = MAX_SEARCH_RESULTS;

		final List<String> ids;
		try {
			ids = DbSearchParser.parseSearch(request.getQuery(), null, SortOrder.FILE.asc()).execute(this.mediaDb, limit, offset);
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Failed to run query: " + e).asRuntimeException());
			return;
		}
		final List<ContentItem> results = this.contentTree.getItemsForIds(ids, null);

		final Builder ret = SearchReply.newBuilder();
		addItemsToSearchReply(ret, results);
		responseObserver.onNext(ret.build());
		responseObserver.onCompleted();
	}

	private static void addItemsToSearchReply(final Builder ret, final List<ContentItem> results) {
		for (final ContentItem i : results) {
			ret.addResult(MediaItem.newBuilder()
					.setId(i.getId())
					.setTitle(i.getTitle())
					.setMimeType(i.getFormat().getMime())
					.setFileLength(i.getFileLength())
					.setDurationMillis(i.getDurationMillis())
					.build());
		}
	}

	@Override
	public void readMedia(final ReadMediaRequest request, final StreamObserver<ReadMediaReply> responseObserver) {
		final ContentItem item = this.contentTree.getItem(request.getId());
		if (item == null) {
			responseObserver.onError(Status.NOT_FOUND.withDescription("Not found.").asRuntimeException());
			return;
		}

		final ContentNode node = this.contentTree.getNode(item.getParentId());
		// TODO once user auth is a thing, check that here to allow access to protected items.
		if (node == null || node.hasAuthList()) {
			responseObserver.onError(Status.NOT_FOUND.withDescription("Not found.").asRuntimeException());
			return;
		}

		final File file = item.getFile();
		if (!file.exists() || file.length() < 1) {
			responseObserver.onError(Status.INTERNAL.withDescription("File missing.").asRuntimeException());
			return;
		}

		try (final BufferedInputStream is = new BufferedInputStream(new FileInputStream(file))) {
			final byte[] buffer = new byte[MESSAGE_SIZE_BYTES];
			boolean first = true;
			int readLength;
			while ((readLength = is.read(buffer, 0, MESSAGE_SIZE_BYTES)) != -1) {
				final ReadMediaReply.Builder builder = ReadMediaReply.newBuilder().setContent(ByteString.copyFrom(buffer, 0, readLength));
				if (first) {
					builder.setTotalFileLength(file.length());
					builder.setMimeType(item.getFormat().getMime());
					first = false;
				}
				responseObserver.onNext(builder.build());
			}
			responseObserver.onCompleted();
		}
		catch (final FileNotFoundException e) {
			responseObserver.onError(Status.INTERNAL.withDescription("File missing.").asRuntimeException());
			return;
		}
		catch (final IOException e) {
			responseObserver.onError(Status.INTERNAL.withDescription("File error.").asRuntimeException());
			return;
		}
	}

}

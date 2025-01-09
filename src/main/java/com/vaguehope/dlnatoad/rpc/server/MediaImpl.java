package com.vaguehope.dlnatoad.rpc.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;
import com.vaguehope.dlnatoad.db.search.SortColumn;
import com.vaguehope.dlnatoad.db.search.SortColumn.SortOrder;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.rpc.MediaGrpc;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.AboutReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.AboutRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ChooseMediaReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ChooseMediaRequest;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.ChooseMethod;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.FileExistance;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.HasMediaReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.HasMediaRequest;
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
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SortBy;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SortDirection;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SortField;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class MediaImpl extends MediaGrpc.MediaImplBase {

	private static final int MAX_SEARCH_RESULTS = 500;
	private static final int MESSAGE_SIZE_BYTES = 256 * 1024;

	// to match search().
	private static final Set<SortField> SUPPORTED_SORT_FIELDS = ImmutableSet.of(
			SortField.UNSPECIFIED_ORDER, SortField.FILE_PATH, SortField.DATE_ADDED, SortField.DURATION, SortField.FILE_SIZE,
			SortField.LAST_PLAYED, SortField.PLAYBACK_STARTED, SortField.PLAYBACK_COMPLETED);
	// to match methods implemented in DbSearchParser.
	private static final Set<ChooseMethod> SUPPORTED_CHOOSE_METHODS = ImmutableSet.of(
			ChooseMethod.UNSPECIFIED_METHOD, ChooseMethod.RANDOM, ChooseMethod.LESS_RECENT, ChooseMethod.LESS_PLAYED);

	private final ContentTree contentTree;
	private final MediaDb mediaDb;

	public MediaImpl(final ContentTree contentTree, final MediaDb mediaDb) {
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
	}

	@Override
	public void about(final AboutRequest request, final StreamObserver<AboutReply> responseObserver) {
		final AboutReply resp = AboutReply.newBuilder()
				.setName(C.APPNAME)
				.addAllSupportedSortField(SUPPORTED_SORT_FIELDS)
				.addAllSupportedChooseMethod(SUPPORTED_CHOOSE_METHODS)
				.build();
		responseObserver.onNext(resp);
		responseObserver.onCompleted();
	}

	@Override
	public void hasMedia(final HasMediaRequest request, final StreamObserver<HasMediaReply> responseObserver) {
		final ContentItem item = getItemCheckingAuth(request.getId(), () -> {
			responseObserver.onNext(HasMediaReply.newBuilder().setExistence(FileExistance.UNKNOWN).build());
			responseObserver.onCompleted();
		});


		if (item == null) return;

		final MediaItem rpcItem = itemToRpcItem(item);

		final Collection<Tag> tags;
		try {
			tags = this.mediaDb.getTags(item.getId(), false, false);
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INTERNAL.withDescription("Failed to read tags from DB.").asRuntimeException());
			return;
		}

		final HasMediaReply.Builder reply = HasMediaReply.newBuilder()
				.setExistence(FileExistance.EXISTS)
				.setItem(rpcItem);

		for (final Tag t : tags) {
			reply.addTag(MediaTag.newBuilder().setTag(t.getTag()).setCls(t.getCls()).setModifiedMillis(t.getModified()).build());
		}

		responseObserver.onNext(reply.build());
		responseObserver.onCompleted();
	}

	@Override
	public void listNode(final ListNodeRequest request, final StreamObserver<ListNodeReply> responseObserver) {
		final ContentNode node = this.contentTree.getNode(request.getNodeId());
		// TODO once user auth is a thing, check that here to allow access to protected items.
		if (node == null || node.hasAuthList()) {
			responseObserver.onError(Status.NOT_FOUND.withDescription("Not found.").asRuntimeException());
			return;
		}

		final ListNodeReply.Builder reply = ListNodeReply.newBuilder();
		reply.setNode(nodeToRpcNode(node));
		for (final ContentNode n : node.nodesUserHasAuth(null)) {
			reply.addChild(nodeToRpcNode(n));
		}
		node.withEachItem((i) -> reply.addItem(itemToRpcItem(i)));

		responseObserver.onNext(reply.build());
		responseObserver.onCompleted();
	}

	@Override
	public void readMedia(final ReadMediaRequest request, final StreamObserver<ReadMediaReply> responseObserver) {
		final ContentItem item = getItemCheckingAuth(request.getId(), responseObserver);
		if (item == null) return;

		final File file = item.getFile();
		if (!file.exists() || file.length() < 1) {
			responseObserver.onError(Status.INTERNAL.withDescription("File missing.").asRuntimeException());
			return;
		}

		final List<Range> ranges = request.getRangeList();
		if (ranges.size() > 1) {
			responseObserver.onError(Status.UNIMPLEMENTED.withDescription("Multiple ranges not implemented.").asRuntimeException());
		}
		final Range range = ranges.size() == 1 ? ranges.get(0) : null;
		final long rangeLength = range != null ? range.getLast() - range.getFirst() + 1 : 0L;

		try (final BufferedInputStream is = new BufferedInputStream(new FileInputStream(file))) {
			final byte[] buffer = new byte[MESSAGE_SIZE_BYTES];
			boolean first = true;
			long totalRead = 0;
			int toReadLength;
			int wasReadLength;

			if (range != null) is.skip(range.getFirst());
			toReadLength = range != null
					? (int) Math.min(rangeLength, MESSAGE_SIZE_BYTES)
					: MESSAGE_SIZE_BYTES;

			while ((wasReadLength = is.read(buffer, 0, toReadLength)) != -1) {
				final ReadMediaReply.Builder builder = ReadMediaReply.newBuilder().setContent(ByteString.copyFrom(buffer, 0, wasReadLength));
				if (first) {
					builder.setTotalFileLength(file.length());
					builder.setMimeType(item.getFormat().getMime());
					first = false;
				}
				if (range != null) builder.setRangeIndex(0);
				responseObserver.onNext(builder.build());

				if (range != null) {
					totalRead += wasReadLength;
					toReadLength = (int) Math.min(rangeLength - totalRead, MESSAGE_SIZE_BYTES);
					if (toReadLength < 1) break;
				}
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

	@Override
	public void search(final SearchRequest request, final StreamObserver<SearchReply> responseObserver) {
		// TODO once user auth is a thing, check that here to allow access to protected items.

		final int maxResults = request.getMaxResults() == 0 ? MAX_SEARCH_RESULTS : request.getMaxResults();
		if (maxResults < 1) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid max_results.").asRuntimeException());
			return;
		}

		final List<SortOrder> sorts = new ArrayList<>();
		for (final SortBy sb : request.getSortByList()) {
			switch (sb.getSortField()) {
			case UNSPECIFIED_ORDER:
			case FILE_PATH:
				sorts.add(direction(SortColumn.FILE_PATH, sb.getDirection()));
				break;
			case DATE_ADDED:
				// TODO MODIFIED is not reallllly the same as ADDED, would be nice to add a column for ADDED.
				sorts.add(direction(SortColumn.MODIFIED, sb.getDirection()));
				break;
			case DURATION:
				sorts.add(direction(SortColumn.DURATION, sb.getDirection()));
				break;
			case FILE_SIZE:
				sorts.add(direction(SortColumn.FILE_SIZE, sb.getDirection()));
				break;
			case LAST_PLAYED:
				sorts.add(direction(SortColumn.LAST_PLAYED, sb.getDirection()));
				break;
			case PLAYBACK_STARTED:
				sorts.add(direction(SortColumn.START_COUNT, sb.getDirection()));
				break;
			case PLAYBACK_COMPLETED:
				sorts.add(direction(SortColumn.COMPLETE_COUNT, sb.getDirection()));
				break;
			default:
				responseObserver.onError(Status.UNIMPLEMENTED.withDescription("Sort column not implemented.").asRuntimeException());
				return;
			}
		}
		if (sorts.size() < 1) sorts.add(SortColumn.FILE_PATH.asc());

		final List<String> ids;
		try {
			ids = DbSearchParser.parseSearch(request.getQuery(), null, sorts).execute(this.mediaDb, maxResults, 0);
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Failed to run query: " + e).asRuntimeException());
			return;
		}
		final List<ContentItem> results = this.contentTree.getItemsForIds(ids, null);

		final SearchReply.Builder ret = SearchReply.newBuilder();
		for (final ContentItem i : results) {
			ret.addResult(itemToRpcItem(i));
		}
		responseObserver.onNext(ret.build());
		responseObserver.onCompleted();
	}

	@Override
	public void chooseMedia(final ChooseMediaRequest request, final StreamObserver<ChooseMediaReply> responseObserver) {
		if (!SUPPORTED_CHOOSE_METHODS.contains(request.getMethod())) {
			responseObserver.onError(Status.UNIMPLEMENTED.withDescription("Method not supported.").asRuntimeException());
			return;
		}

		final List<String> ids;
		try {
			ids = DbSearchParser.parseSearchForChoose(request.getQuery(), null, request.getMethod()).execute(this.mediaDb, request.getCount(), 0);
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Failed to run query: " + e).asRuntimeException());
			return;
		}

		final List<ContentItem> results = this.contentTree.getItemsForIds(ids, null);

		final ChooseMediaReply.Builder ret = ChooseMediaReply.newBuilder();
		for (final ContentItem i : results) {
			ret.addItem(itemToRpcItem(i));
		}
		responseObserver.onNext(ret.build());
		responseObserver.onCompleted();
	}

	@Override
	public void recordPlayback(final RecordPlaybackRequest request, final StreamObserver<RecordPlaybackReply> responseObserver) {
		final long millisAgo = System.currentTimeMillis() - request.getStartTimeMillis();
		if (millisAgo > TimeUnit.HOURS.toMillis(24)) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("start_time_millis more than 24h ago.").asRuntimeException());
			return;
		}
		else if (millisAgo < 0) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("start_time_millis is in the future.").asRuntimeException());
			return;
		}

		final ContentItem item = getItemCheckingAuth(request.getId(), responseObserver);
		if (item == null) return;

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.recordPlayback(request.getId(), request.getStartTimeMillis(), request.getCompleted());
		}
		catch (final IOException | SQLException e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
			return;
		}
	}

	private ContentItem getItemCheckingAuth(final String id, final StreamObserver<?> responseObserver) {
		return getItemCheckingAuth(id, () -> {
			responseObserver.onError(Status.NOT_FOUND.withDescription("Not found.").asRuntimeException());
		});
	}

	/**
	 * if response is null then error has already been returned to caller.
	 */
	private ContentItem getItemCheckingAuth(final String itemId, final Runnable onNotFound) {
		final ContentItem item = this.contentTree.getItem(itemId);
		if (item == null) {
			onNotFound.run();
			return null;
		}

		final ContentNode node = this.contentTree.getNode(item.getParentId());
		// TODO once user auth is a thing, check that here to allow access to protected items.
		if (node == null || node.hasAuthList()) {
			onNotFound.run();
			return null;
		}

		return item;
	}

	private static SortOrder direction(final SortColumn order, final SortDirection direction) {
		switch (direction) {
		case UNSPECIFIED_DIRECTION:
		case ASC:
			return order.asc();
		case DESC:
			return order.desc();
		default:
			throw new IllegalArgumentException();
		}
	}

	private static MediaNode nodeToRpcNode(final ContentNode node) {
		return MediaNode.newBuilder()
				.setId(node.getId())
				.setTitle(node.getTitle())
				.setParentId(node.getParentId())
				.build();
	}

	private static MediaItem itemToRpcItem(final ContentItem item) {
		return MediaItem.newBuilder()
				.setId(item.getId())
				.setTitle(item.getTitle())
				.setMimeType(item.getFormat().getMime())
				.setFileLength(item.getFileLength())
				.setDurationMillis(item.getDurationMillis())
				.build();
	}

}

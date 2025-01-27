package com.vaguehope.dlnatoad.rpc.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.Permission;
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
import com.vaguehope.dlnatoad.rpc.MediaToadProto.TagAction;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.TagChange;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.UpdateTagsReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.UpdateTagsRequest;

import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.prometheus.metrics.core.metrics.Counter;

public class MediaImpl extends MediaGrpc.MediaImplBase {

	private static final int MAX_SEARCH_RESULTS = 500;
	private static final int MESSAGE_SIZE_BYTES = 256 * 1024;

	private static final Counter CHUNKS_SENT_METRIC = Counter.builder()
			.name("rpc_chunks_sent")
			.help("count of chunks of media file that have been written to the grpc output buffer.")
			.register();

	// to match search().
	private static final Set<SortField> SUPPORTED_SORT_FIELDS = ImmutableSet.of(
			SortField.UNSPECIFIED_ORDER, SortField.FILE_PATH, SortField.DATE_ADDED, SortField.DURATION, SortField.FILE_SIZE,
			SortField.LAST_PLAYED, SortField.PLAYBACK_STARTED, SortField.PLAYBACK_COMPLETED);
	// to match methods implemented in DbSearchParser.
	private static final Set<ChooseMethod> SUPPORTED_CHOOSE_METHODS = ImmutableSet.of(
			ChooseMethod.UNSPECIFIED_METHOD, ChooseMethod.RANDOM, ChooseMethod.LESS_RECENT, ChooseMethod.LESS_PLAYED);

	private final ContentTree contentTree;
	private final MediaDb mediaDb;

	private final Cache<String, Boolean> recentlyReportedPlaybacks = CacheBuilder.newBuilder()
			.expireAfterWrite(15, TimeUnit.MINUTES).build();

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
		final String username = JwtInterceptor.USERNAME_CONTEXT_KEY.get();

		final ContentItem item = getItemCheckingAuth(username, request.getId(), () -> {
			responseObserver.onNext(HasMediaReply.newBuilder().setExistence(FileExistance.UNKNOWN).build());
			responseObserver.onCompleted();
		});

		if (item == null) return;

		final Collection<Tag> tags;
		try {
			tags = this.mediaDb.getTags(item.getId(), false, false);
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INTERNAL.withDescription("Failed to read tags from DB.").asRuntimeException());
			return;
		}
		final MediaItem rpcItem = itemToRpcItem(item, tags);

		final HasMediaReply.Builder reply = HasMediaReply.newBuilder()
				.setExistence(FileExistance.EXISTS)
				.setItem(rpcItem);

		responseObserver.onNext(reply.build());
		responseObserver.onCompleted();
	}

	@Override
	public void listNode(final ListNodeRequest request, final StreamObserver<ListNodeReply> responseObserver) {
		final String username = JwtInterceptor.USERNAME_CONTEXT_KEY.get();

		final ContentNode node = this.contentTree.getNode(request.getNodeId());
		if (node == null || !node.isUserAuth(username)) {
			responseObserver.onError(Status.NOT_FOUND.withDescription("Not found.").asRuntimeException());
			return;
		}

		final ListNodeReply.Builder reply = ListNodeReply.newBuilder();
		reply.setNode(nodeToRpcNode(node));
		for (final ContentNode n : node.nodesUserHasAuth(username)) {
			reply.addChild(nodeToRpcNode(n));
		}

		try {
			node.withEachItem((i) -> reply.addItem(itemToRpcItem(i, this.mediaDb.getTags(i.getId(), false, false))));
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		}

		responseObserver.onNext(reply.build());
		responseObserver.onCompleted();
	}

	@Override
	public void readMedia(final ReadMediaRequest request, final StreamObserver<ReadMediaReply> responseObserver) {
		final String username = JwtInterceptor.USERNAME_CONTEXT_KEY.get();

		final ContentItem item = getItemCheckingAuth(username, request.getId(), responseObserver);
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

		final ServerCallStreamObserver<ReadMediaReply> serverCallStreamObserver = (ServerCallStreamObserver<ReadMediaReply>) responseObserver;
		serverCallStreamObserver.setMessageCompression(false);  // no point trying to compress media files.
		serverCallStreamObserver.setOnReadyThreshold(4 * MESSAGE_SIZE_BYTES); // default is 32kib io.grpc.internal.AbstractStream.TransportState.DEFAULT_ONREADY_THRESHOLD

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

			// ideally this would be implemented async, but given expected server load is very low this simpler blocking impl will do for now.
			while ((wasReadLength = is.read(buffer, 0, toReadLength)) != -1) {
				// fae suspects this blocking impl is preventing the async deadline set in io.grpc.servlet.ServletAdapter
				// from working, so add our own deadline tracking for safety.
				if (!serverCallStreamObserver.isReady() || serverCallStreamObserver.isCancelled()) {
					final Deadline deadline = Deadline.after(5, TimeUnit.MINUTES);
					while (!serverCallStreamObserver.isReady() && !serverCallStreamObserver.isCancelled() && !deadline.isExpired()) Thread.sleep(100L);
					if (serverCallStreamObserver.isCancelled()) {
						responseObserver.onError(Status.CANCELLED.asRuntimeException());
						return;
					}
					if (deadline.isExpired()) {
						responseObserver.onError(Status.DEADLINE_EXCEEDED.asRuntimeException());
						return;
					}
				}

				final ReadMediaReply.Builder builder = ReadMediaReply.newBuilder().setContent(ByteString.copyFrom(buffer, 0, wasReadLength));
				if (first) {
					builder.setTotalFileLength(file.length());
					builder.setMimeType(item.getFormat().getMime());
					first = false;
				}
				if (range != null) builder.setRangeIndex(0);
				responseObserver.onNext(builder.build());
				CHUNKS_SENT_METRIC.inc();

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
		catch (final InterruptedException e) {
			// ignore.
		}
	}

	@Override
	public void search(final SearchRequest request, final StreamObserver<SearchReply> responseObserver) {
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

		final String username = JwtInterceptor.USERNAME_CONTEXT_KEY.get();
		final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);

		final Map<String, List<Tag>> ids;
		try {
			ids = DbSearchParser.parseSearchWithTags(request.getQuery(), authIds, sorts).execute(this.mediaDb, maxResults, 0);
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Failed to run query: " + e).asRuntimeException());
			return;
		}
		final List<ContentItem> results = this.contentTree.getItemsForIds(ids.keySet(), username);

		final SearchReply.Builder ret = SearchReply.newBuilder();
		for (final ContentItem i : results) {
			ret.addResult(itemToRpcItem(i, ids.get(i.getId())));
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

		final String username = JwtInterceptor.USERNAME_CONTEXT_KEY.get();
		final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);

		final List<String> ids;
		try {
			ids = DbSearchParser.parseSearchForChoose(request.getQuery(), authIds, request.getMethod()).execute(this.mediaDb, request.getCount(), 0);
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Failed to run query: " + e).asRuntimeException());
			return;
		}

		final List<ContentItem> results = this.contentTree.getItemsForIds(ids, username);

		final ChooseMediaReply.Builder ret = ChooseMediaReply.newBuilder();
		try {
			for (final ContentItem i : results) {
				ret.addItem(itemToRpcItem(i, this.mediaDb.getTags(i.getId(), false, false)));
			}
		}
		catch (final SQLException e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
			return;
		}

		responseObserver.onNext(ret.build());
		responseObserver.onCompleted();
	}

	@Override
	public void recordPlayback(final RecordPlaybackRequest request, final StreamObserver<RecordPlaybackReply> responseObserver) {
		if (this.recentlyReportedPlaybacks.getIfPresent(request.getId()) != null) {
			responseObserver.onError(Status.ALREADY_EXISTS.withDescription("playback already reported recently.").asRuntimeException());
			return;
		}

		final long millisAgo = System.currentTimeMillis() - request.getStartTimeMillis();
		if (millisAgo > TimeUnit.HOURS.toMillis(24)) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("start_time_millis more than 24h ago.").asRuntimeException());
			return;
		}
		else if (millisAgo < 0) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("start_time_millis is in the future.").asRuntimeException());
			return;
		}

		final String username = JwtInterceptor.USERNAME_CONTEXT_KEY.get();

		final ContentItem item = getItemCheckingAuth(username, request.getId(), responseObserver);
		if (item == null) return;

		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.recordPlayback(request.getId(), request.getStartTimeMillis(), request.getCompleted());
		}
		catch (final IOException | SQLException e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
			return;
		}

		responseObserver.onNext(RecordPlaybackReply.newBuilder().build());
		responseObserver.onCompleted();

		this.recentlyReportedPlaybacks.put(request.getId(), Boolean.TRUE);
	}

	@Override
	public void updateTags(final UpdateTagsRequest request, final StreamObserver<UpdateTagsReply> responseObserver) {
		final Set<Permission> permissions = JwtInterceptor.PERMISSIONS_CONTEXT_KEY.get();
		if (permissions == null || !permissions.contains(Permission.EDITTAGS)) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Not permitted.").asRuntimeException());
			return;
		}

		if (request.getChangeCount() > 100) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("too many changes in one request.").asRuntimeException());
			return;
		}
		for (final TagChange change : request.getChangeList()) {
			if (StringUtils.isBlank(change.getId())) {
				responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("missing id.").asRuntimeException());
				return;
			}
			if (change.getAction() != TagAction.ADD && change.getAction() != TagAction.REMOVE) {
				responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("unsupported action.").asRuntimeException());
				return;
			}
			if (change.getTagCount() < 1) {
				responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("empty tag list.").asRuntimeException());
				return;
			}
			for (final MediaTag tag : change.getTagList()) {
				if (StringUtils.isBlank(tag.getTag())) {
					responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("missing tag.").asRuntimeException());
					return;
				}
				if (StringUtils.containsWhitespace(tag.getCls())) {
					responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("cls must not contain whitespace.").asRuntimeException());
					return;
				}
			}
		}

		// check items exist and user had access.
		final String username = JwtInterceptor.USERNAME_CONTEXT_KEY.get();
		for (final TagChange change : request.getChangeList()) {
			final ContentItem item = getItemCheckingAuth(username, change.getId(), responseObserver, Permission.EDITTAGS);
			if (item == null) return;
		}

		final long now = System.currentTimeMillis();
		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			for (final TagChange change : request.getChangeList()) {
				switch (change.getAction()) {
				case ADD:
					for (final MediaTag t : change.getTagList()) {
						w.addTag(change.getId(), t.getTag(), t.getCls(), now);
					}
					break;
				case REMOVE:
					for (final MediaTag t : change.getTagList()) {
						w.setTagModifiedAndDeleted(change.getId(), t.getTag(), t.getCls(), true, now);
					}
					break;
				default:
					throw new IllegalStateException();
				}
			}
		}
		catch (final IOException | SQLException e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
			return;
		}

		responseObserver.onNext(UpdateTagsReply.newBuilder().build());
		responseObserver.onCompleted();

		// TODO update autocomplete
	}

	private ContentItem getItemCheckingAuth(final String username, final String id, final StreamObserver<?> responseObserver) {
		return getItemCheckingAuth(username, id, responseObserver, null);
	}

	private ContentItem getItemCheckingAuth(final String username, final String id, final StreamObserver<?> responseObserver,  final Permission permission) {
		return getItemCheckingAuth(username, id, () -> {
			responseObserver.onError(Status.NOT_FOUND.withDescription("Not found.").asRuntimeException());
		}, permission, () -> {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Not permitted.").asRuntimeException());
		});
	}

	private ContentItem getItemCheckingAuth(final String username, final String itemId, final Runnable onNotFound) {
		return getItemCheckingAuth(username, itemId, onNotFound, null, null);
	}

	/**
	 * if response is null then error has already been returned to caller.
	 */
	private ContentItem getItemCheckingAuth(final String username, final String itemId, final Runnable onNotFound, final Permission permission, final Runnable onNotPermission) {
		final ContentItem item = this.contentTree.getItem(itemId);
		if (item == null) {
			onNotFound.run();
			return null;
		}

		final ContentNode node = this.contentTree.getNode(item.getParentId());
		if (node == null || !node.isUserAuth(username)) {
			onNotFound.run();
			return null;
		}

		if (permission != null && node.hasAuthList() && !node.isUserAuthWithPermission(username, permission)) {
			onNotPermission.run();
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

	private static MediaItem itemToRpcItem(final ContentItem item, final Collection<Tag> tags) {
		final MediaItem.Builder builder = MediaItem.newBuilder()
				.setId(item.getId())
				.setTitle(item.getTitle())
				.setMimeType(item.getFormat().getMime())
				.setFileLength(item.getFileLength())
				.setDurationMillis(item.getDurationMillis());

		if (tags != null) {
			for (final Tag t : tags) {
				builder.addTag(MediaTag.newBuilder()
						.setTag(t.getTag())
						.setCls(t.getCls())
						.setModifiedMillis(t.getModified())
						.build());
			}
		}

		return builder.build();
	}

}

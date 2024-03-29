package com.vaguehope.dlnatoad.tagdeterminer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.Args.ArgsException;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;
import com.vaguehope.dlnatoad.db.search.DbSearchSyntax;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.rpc.RpcTarget;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerGrpc.TagDeterminerFutureStub;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerGrpc.TagDeterminerStub;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerProto.AboutReply;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerProto.AboutRequest;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerProto.DetermineTagsReply;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerProto.DetermineTagsRequest;
import com.vaguehope.dlnatoad.util.ExceptionHelper;
import com.vaguehope.dlnatoad.util.ExecutorHelper;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

public class TagDeterminerController {

	private static final long WORK_FINDER_INTERVAL_SECONDS = TimeUnit.MINUTES.toSeconds(1);
	private static final int QUERY_LIMIT = 10;
	private static final int BATCH_WRITE_INTERVAL_SECONDS = 30;

	private static final int RPC_DEADLINE_SECONDS = 30;
	private static final int MESSAGE_SIZE_BYTES = 256 * 1024;
	private static final Logger LOG = LoggerFactory.getLogger(TagDeterminerController.class);

	private final MediaDb db;
	private final ContentTree contentTree;
	private final ScheduledExecutorService schExSvc;
	private final Clock clock;

	private final List<TagDeterminer> determiners = new CopyOnWriteArrayList<>();
	private final Map<TagDeterminer, Boolean> determinerStatus = new ConcurrentHashMap<>();
	private final Map<TagDeterminer, Long> lastDbVersion = new ConcurrentHashMap<>();

	private final Map<TagDeterminer, ManagedChannel> managedChannels = new ConcurrentHashMap<>();
	private final Map<TagDeterminer, TagDeterminerStub> stubs = new ConcurrentHashMap<>();
	private final Map<TagDeterminer, TagDeterminerFutureStub> futureStubs = new ConcurrentHashMap<>();

	private final Deque<Runnable> workQueue = new ConcurrentLinkedDeque<>();
	private final BlockingQueue<TDResponse> storeDuraionQueue = new LinkedBlockingQueue<>();

	public TagDeterminerController(final Args args, final ContentTree contentTree, final MediaDb db) throws ArgsException {
		this(args, contentTree, db, ExecutorHelper.newScheduledExecutor(1, "td"), Clock.systemUTC());
	}

	TagDeterminerController(final Args args, final ContentTree contentTree, final MediaDb db, final ScheduledExecutorService schExSvc, final Clock clock) throws ArgsException {
		this.contentTree = contentTree;
		this.db = db;
		this.schExSvc = schExSvc;
		this.clock = clock;
		for (final String arg : args.getTagDeterminers()) {
			this.determiners.add(parseArg(arg));
		}
	}

	private static TagDeterminer parseArg(final String arg) throws ArgsException {
		final String[] parts = StringUtils.split(arg, "|", 2);
		if (parts.length < 2) throw new ArgsException("Invalid URL and query: " + arg);
		final RpcTarget target = RpcTarget.fromHttpUrl(parts[0]);
		return new TagDeterminer(target, parts[1]);
	}

	public void start() {
		if (this.determiners.size() < 1) return;

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				shutdown();
			}
		});

		for (final TagDeterminer d : this.determiners) {
			final ManagedChannel channel = d.getTarget().makeChannelBuilder().build();
			this.managedChannels.put(d, channel);
			this.stubs.put(d, TagDeterminerGrpc.newStub(channel));
			this.futureStubs.put(d, TagDeterminerGrpc.newFutureStub(channel));
		}

		this.schExSvc.scheduleWithFixedDelay(this::findWork, 0, WORK_FINDER_INTERVAL_SECONDS, TimeUnit.SECONDS);
		this.schExSvc.scheduleWithFixedDelay(this::batchWrite, BATCH_WRITE_INTERVAL_SECONDS, BATCH_WRITE_INTERVAL_SECONDS, TimeUnit.SECONDS);
		LOG.info("Tag determiner started: {}", this.determiners);
	}

	public void shutdown() {
		for (final ManagedChannel c : this.managedChannels.values()) {
			c.shutdown();
		}
		for (final ManagedChannel c : this.managedChannels.values()) {
			try {
				c.awaitTermination(30, TimeUnit.SECONDS);
			}
			catch (final InterruptedException e1) {
				// oh well we tried.
			}
		}
	}

	private void scheduleWorker() {
		this.schExSvc.execute(this::worker);
	}

	private void worker() {
		final Runnable r = this.workQueue.poll();
		if (r == null) return;
		try {
			r.run();
		}
		catch (final Throwable e) {
			LOG.error("Error while running task.", e);
		}
		finally {
			scheduleWorker();
		}
	}

	void findWork() {
		if (this.workQueue.size() > 0 || this.storeDuraionQueue.size() > 0) return;

		for (final Entry<TagDeterminer, TagDeterminerFutureStub> m : this.futureStubs.entrySet()) {
			startFindingWorkForDeterminer(m.getKey(), m.getValue());
		}
	}

	private void startFindingWorkForDeterminer(final TagDeterminer determiner, final TagDeterminerFutureStub stub) {
		final Long lastDbVer = this.lastDbVersion.get(determiner);
		if (lastDbVer != null && lastDbVer == this.db.getWriteCount()) return;

		final ListenableFuture<AboutReply> f = stub
				.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
				.about(AboutRequest.newBuilder().build());
		FluentFuture.from(f).addCallback(new FutureCallback<>() {
			@Override
			public void onSuccess(final AboutReply about) {
				if (!Boolean.TRUE.equals(TagDeterminerController.this.determinerStatus.put(determiner, Boolean.TRUE))) {
					LOG.warn("Determiner {} is up: {{}}", determiner.getTarget(), about.toString().replace('\n', ' '));
				}

				findItems(determiner, about);
			}
			@Override
			public void onFailure(final Throwable t) {
				if (!Boolean.FALSE.equals(TagDeterminerController.this.determinerStatus.put(determiner, Boolean.FALSE))) {
					LOG.warn("Determiner {} is down: {}", determiner.getTarget(), ExceptionHelper.causeTrace(t));
				}
			}
		}, this.schExSvc);
	}

	private void findItems(final TagDeterminer determiner, final AboutReply about) {
		final String tagCls = about.getTagCls();
		if (tagCls.length() < 5 || tagCls.strip().length() != tagCls.length()) {
			LOG.warn("Determiner {} has invalid tag_cls: '{}'", determiner, tagCls);
			return;
		}

		// TODO make supported formats something the TD returns in About().
		final String query = String.format("( %s ) -%s type=image", determiner.getQuery(), DbSearchSyntax.makeSingleTagSearch(tagCls));

		final long dbWriteCount = this.db.getWriteCount();
		final List<String> ids;
		try {
			ids = DbSearchParser.parseSearchWithAuthBypass(query).execute(this.db, QUERY_LIMIT, 0);
		}
		catch (final SQLException e) {
			LOG.warn("Failed to query DB for items.", e);
			return;
		}

		if (ids.size() < 1) {
			this.lastDbVersion.put(determiner, dbWriteCount);
			LOG.info("Determiner {} query returned 0 results, sleeping until DB changes.", determiner);
		}

		for (final String id : ids) {
			final ContentItem item = this.contentTree.getItem(id);
			if (item == null) {
				LOG.error("ID from DB not found in contentTree: {}", id);
				return;
			}
			this.workQueue.add(() -> {
				sendItemToDetminer(determiner, about, item);
			});
		}

		scheduleWorker();
	}

	private void sendItemToDetminer(final TagDeterminer determiner, final AboutReply about, final ContentItem item) {
		final TagDeterminerStub stub = this.stubs.get(determiner);

		final CountDownLatch latch = new CountDownLatch(1);
		final StreamObserver<DetermineTagsRequest> reqObs = stub
				.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
				.determineTags(new StreamObserver<>() {
			@Override
			public void onNext(final DetermineTagsReply reply) {
				// Processing responses takes priority over sending more work.
				TagDeterminerController.this.workQueue.push(() -> {
					responseFromDeterminer(determiner, about, item, reply);
				});
				scheduleWorker();
			}
			@Override
			public void onError(final Throwable t) {
				latch.countDown();
				LOG.warn("Receieved error from TagDeterminer DetermineTags():", t);
			}
			@Override
			public void onCompleted() {
				latch.countDown();
			}
		});

		try (final BufferedInputStream is = new BufferedInputStream(new FileInputStream(item.getFile()))) {
			final byte[] buffer = new byte[MESSAGE_SIZE_BYTES];
			int readLength;
			while ((readLength = is.read(buffer, 0, MESSAGE_SIZE_BYTES)) != -1) {
				final DetermineTagsRequest req = DetermineTagsRequest.newBuilder()
						.setFileExt(item.getFormat().getExt())  // TODO only send on first part?
						.setContent(ByteString.copyFrom(buffer, 0, readLength))
						.build();
				reqObs.onNext(req);
			}
			reqObs.onCompleted();
		}
		catch (final Exception e) {
			reqObs.onError(e);
			LOG.warn("Failed to call TagDeterminer DetermineTags():", e);
		}

		try {
			latch.await();
		}
		catch (final InterruptedException e) {}
	}

	private void responseFromDeterminer(final TagDeterminer determiner, final AboutReply about, final ContentItem item, final DetermineTagsReply reply) {
		if (validTags(reply.getTagList())) {
			LOG.info("{} {}: {}", about.getTagCls(), item.getFile(), reply.getTagList());
			this.storeDuraionQueue.add(new TDResponse(about, item, reply));
		}
		else {
			LOG.info("Invalid tags from {} for {}: {}", determiner.getTarget(), item.getFile(), reply.getTagList());
		}
	}

	private static boolean validTags(final List<String> tags) {
		boolean valid = true;
		for (final String t : tags) {
			if (t.length() < 2
					|| t.strip().length() != t.length()) {
				valid = false;
				break;
			}
		}
		return valid;
	}

	void batchWrite() {
		try {
			final List<TDResponse> todo = new ArrayList<>();
			this.storeDuraionQueue.drainTo(todo);
			if (todo.size() < 1) return;

			try (final WritableMediaDb w = this.db.getWritable()) {
				for (final TDResponse r : todo) {
					w.addTag(r.item.getId(), r.about.getTagCls(), "." + r.about.getTagCls(), this.clock.millis());
					for (final String tag : r.reply.getTagList()) {
						w.addTagIfNotDeleted(r.item.getId(), tag, r.about.getTagCls(), this.clock.millis());
					}
				}
			}
			LOG.info("Batch tag write for {} items.", todo.size());
		}
		catch (final Exception e) {
			LOG.error("Scheduled batch tag writer error.", e);
		}
	}

	private static class TDResponse {
		final AboutReply about;
		final ContentItem item;
		final DetermineTagsReply reply;

		public TDResponse(final AboutReply about, final ContentItem item, final DetermineTagsReply reply) {
			this.about = about;
			this.item = item;
			this.reply = reply;
		}
	}

	// for testing:

	void addStubs(final TagDeterminer determiner, final TagDeterminerStub stub, final TagDeterminerFutureStub futureStub) {
		this.determiners.add(determiner);
		this.stubs.put(determiner, stub);
		this.futureStubs.put(determiner, futureStub);
	}

	int queueSize() {
		return this.workQueue.size();
	}

}

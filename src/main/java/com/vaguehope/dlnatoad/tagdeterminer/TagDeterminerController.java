package com.vaguehope.dlnatoad.tagdeterminer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.sql.SQLException;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
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
import com.vaguehope.dlnatoad.util.ExecutorHelper;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

public class TagDeterminerController {

	private static final long WORK_FINDER_INTERVAL_SECONDS = TimeUnit.MINUTES.toSeconds(5);
	private static final int QUERY_LIMIT = 10;
	private static final int MESSAGE_SIZE_BYTES = 256 * 1024;
	private static final Logger LOG = LoggerFactory.getLogger(TagDeterminerController.class);

	private final MediaDb db;
	private final ContentTree contentTree;
	private final ScheduledExecutorService schExSvc;

	private final List<TagDeterminer> determiners = new CopyOnWriteArrayList<>();
	private final Map<TagDeterminer, ManagedChannel> managedChannels = new ConcurrentHashMap<>();
	private final Map<TagDeterminer, TagDeterminerStub> stubs = new ConcurrentHashMap<>();
	private final Map<TagDeterminer, TagDeterminerFutureStub> futureStubs = new ConcurrentHashMap<>();
	private final Deque<Runnable> workQueue = new ConcurrentLinkedDeque<>();

	public TagDeterminerController(final Args args, final ContentTree contentTree, final MediaDb db) throws ArgsException {
		this(args, contentTree, db, ExecutorHelper.newScheduledExecutor(1, "td"));
	}

	TagDeterminerController(final Args args, final ContentTree contentTree, final MediaDb db, final ScheduledExecutorService schExSvc) throws ArgsException {
		this.contentTree = contentTree;
		this.db = db;
		this.schExSvc = schExSvc;
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

		this.schExSvc.submit(() -> {
			this.schExSvc.scheduleWithFixedDelay(this::findWork, WORK_FINDER_INTERVAL_SECONDS, WORK_FINDER_INTERVAL_SECONDS, TimeUnit.SECONDS);
		});
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
		r.run();
	}

	private void findWork() {
		try {
			findWorkOrThrow();
		}
		catch (final Exception e) {
			LOG.warn("Failed to find work: {}", e);
		}
	}

	void findWorkOrThrow() throws SQLException {
		if (this.workQueue.size() > 0) return;

		for (final Entry<TagDeterminer, TagDeterminerFutureStub> m : this.futureStubs.entrySet()) {
			startFindingWorkForDeterminer(m.getKey(), m.getValue());
		}
	}

	private void startFindingWorkForDeterminer(final TagDeterminer determiner, final TagDeterminerFutureStub stub) throws SQLException {
		final ListenableFuture<AboutReply> f = stub.about(AboutRequest.newBuilder().build());
		FluentFuture.from(f).addCallback(new FutureCallback<>() {
			@Override
			public void onSuccess(final AboutReply result) {
				findItems(determiner, result);
			}
			@Override
			public void onFailure(final Throwable t) {
				LOG.warn("Failed to call TagDeterminer About(): {}", t);
			}
		}, this.schExSvc);
	}

	private void findItems(final TagDeterminer determiner, final AboutReply about) {
		try {
			findItemsOrThrow(determiner, about);
		}
		catch (final Exception e) {
			LOG.warn("Failed to find items: {}", e);
		}
	}

	private void findItemsOrThrow(final TagDeterminer determiner, final AboutReply about) throws SQLException {
		final String query = String.format("( %s ) -%s", determiner.getQuery(), DbSearchSyntax.makeSingleTagSearch(about.getAlreadyProcessedTag().getTag()));
		final List<String> ids = DbSearchParser.parseSearchWithAuthBypass(query).execute(this.db, QUERY_LIMIT, 0);

		for (final String id : ids) {
			final ContentItem item = this.contentTree.getItem(id);
			if (item == null) throw new IllegalStateException("ID not found in contentTree: " + id);
			this.workQueue.add(() -> {
				sendItemToDetminer(determiner, item);
			});
		}

		scheduleWorker();
	}

	private void sendItemToDetminer(final TagDeterminer determiner, final ContentItem item) {
		final TagDeterminerStub stub = this.stubs.get(determiner);
		final StreamObserver<DetermineTagsRequest> reqObs = stub.determineTags(new StreamObserver<>() {
			@Override
			public void onNext(final DetermineTagsReply reply) {
				// Processing responses takes priority over sending more work.
				TagDeterminerController.this.workQueue.push(() -> {
					responseFromDeterminer(determiner, item, reply);
				});
			}
			@Override
			public void onError(final Throwable t) {
				LOG.warn("Receieved error from TagDeterminer DetermineTags(): {}", t);
				scheduleWorker();
			}
			@Override
			public void onCompleted() {
				scheduleWorker();
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
			LOG.warn("Failed to call TagDeterminer DetermineTags(): {}", e);
		}
	}

	protected void responseFromDeterminer(final TagDeterminer determiner, final ContentItem item, final DetermineTagsReply reply) {
		scheduleWorker();

		LOG.info("Response from {} for {}: {}", determiner.getTarget(), item.getFile(), reply.getTagList());
		// TODO add tags to DB, maybe some batching, etc.
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

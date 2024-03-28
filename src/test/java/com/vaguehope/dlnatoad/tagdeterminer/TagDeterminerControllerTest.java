package com.vaguehope.dlnatoad.tagdeterminer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.MockMediaMetadataStore;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.rpc.RpcTarget;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerGrpc.TagDeterminerFutureStub;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerGrpc.TagDeterminerStub;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerProto.AboutReply;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerProto.AboutRequest;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerProto.DetermineTagsReply;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerProto.DetermineTagsRequest;

import io.grpc.stub.StreamObserver;

public class TagDeterminerControllerTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private ScheduledExecutorService schEx;
	private ContentTree contentTree;
	private MockMediaMetadataStore mockMediaMetadataStore;
	private MediaDb db;
	private Clock clock;
	private TagDeterminerController undertest;

	private TagDeterminer determiner;
	private TagDeterminerStub stub;
	private TagDeterminerFutureStub futureStub;


	@Before
	public void before() throws Exception {
		this.schEx = mock(ScheduledExecutorService.class);
		doAnswer(inv -> {
				inv.getArgument(0, Runnable.class).run();
				return null;
			}).when(this.schEx).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
		doAnswer(inv -> {
			inv.getArgument(0, Runnable.class).run();
			return null;
		}).when(this.schEx).execute(any(Runnable.class));

		final Args args = mock(Args.class);
		this.contentTree = new ContentTree();
		this.mockMediaMetadataStore = MockMediaMetadataStore.withMockExSvc(this.tmp);
		this.db = this.mockMediaMetadataStore.getMediaDb();
		this.clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
		this.undertest = new TagDeterminerController(args, this.contentTree, this.db, this.schEx, this.clock);

		this.determiner = new TagDeterminer(new RpcTarget("http://example.com/", true), "f~myphotos");

		this.stub = mock(TagDeterminerStub.class);
		this.futureStub = mock(TagDeterminerFutureStub.class);
		when(this.stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(this.stub);
		when(this.futureStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(this.futureStub);

		this.undertest.addStubs(this.determiner, this.stub, this.futureStub);
	}

	@Test
	public void itDoesSomething() throws Exception {
		final ListenableFuture<AboutReply> aboutFuture = Futures.immediateFuture(AboutReply.newBuilder()
				.setName("Fake System Thingy")
				.setTagCls("FakeSystem")
				.build());
		when(this.futureStub.about(any(AboutRequest.class))).thenReturn(aboutFuture);

		// TODO mocking content and mocking DB should interact with each other better.
		final String idA = this.mockMediaMetadataStore.addFileWithName("myphotos A");
		final File fileA = new File(this.db.getFilePathForId(idA));
		this.contentTree.addItem(new ContentItem(idA, "0", "my item A", fileA, MediaFormat.JPEG));

		// Item that has already been sent.
		final String idB = this.mockMediaMetadataStore.addFileWithNameAndTags("myphotos B", "FakeSystem");
		final File fileB = new File(this.db.getFilePathForId(idB));
		this.contentTree.addItem(new ContentItem(idB, "0", "my item B", fileB, MediaFormat.JPEG));

		this.mockMediaMetadataStore.addNoiseToDb();

		@SuppressWarnings("unchecked")
		final ArgumentCaptor<StreamObserver<DetermineTagsReply>> cap = ArgumentCaptor.forClass(StreamObserver.class);
		final StreamObserver<DetermineTagsRequest> reqObs = mock(StreamObserver.class);
		when(this.stub.determineTags(cap.capture())).thenReturn(reqObs);
		doAnswer(a -> {
			cap.getValue().onCompleted();
			return null;
		}).when(reqObs).onCompleted();

		this.undertest.findWork();

		final InOrder uploadOrder = inOrder(reqObs);
		uploadOrder.verify(reqObs).onNext(DetermineTagsRequest.newBuilder()
				.setFileExt("jpeg")
				.setContent(ByteString.copyFrom(FileUtils.readFileToByteArray(fileA)))
				.build());
		uploadOrder.verify(reqObs).onCompleted();
		uploadOrder.verifyNoMoreInteractions();

		final StreamObserver<DetermineTagsReply> respObs = cap.getValue();
		respObs.onNext(DetermineTagsReply.newBuilder()
				.addTag("a_thing")
				.addTag("another_thing")
				.build());
		respObs.onCompleted();

		this.undertest.batchWrite();

		final Collection<Tag> tagsA = this.db.getTags(idA, true);
		assertThat(tagsA, containsInAnyOrder(
				new Tag("FakeSystem", ".FakeSystem", this.clock.millis(), false),
				new Tag("a_thing", "FakeSystem", this.clock.millis(), false),
				new Tag("another_thing", "FakeSystem", this.clock.millis(), false)
				));

		final Collection<Tag> tagsB = this.db.getTags(idB, true);
		assertEquals(1, tagsB.size());
		assertEquals("FakeSystem", tagsB.iterator().next().getTag());

		assertEquals(0, this.undertest.queueSize());
	}

}

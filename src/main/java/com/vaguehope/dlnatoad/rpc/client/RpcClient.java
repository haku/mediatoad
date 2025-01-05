package com.vaguehope.dlnatoad.rpc.client;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.vaguehope.common.rpc.RpcTarget;
import com.vaguehope.common.rpc.RpcTarget.RpcConfigException;
import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.Args.ArgsException;
import com.vaguehope.dlnatoad.rpc.MediaGrpc;
import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaBlockingStub;
import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaFutureStub;

import io.grpc.ManagedChannel;

public class RpcClient {

	private final List<RemoteInstance> remoteInstances = new CopyOnWriteArrayList<>();
	private final Map<RemoteInstance, ManagedChannel> managedChannels = new ConcurrentHashMap<>();
	private final Map<String, MediaFutureStub> mediaFutureStubs = new ConcurrentHashMap<>();
	private final Map<String, MediaBlockingStub> mediaBlockingStubs = new ConcurrentHashMap<>();

	public RpcClient(final Args args) throws ArgsException {
		parseArgs(args);
	}

	public void start() {
		for (final RemoteInstance ri : this.remoteInstances) {
			startChannel(ri);
		}
	}

	public void shutdown() {
		for (final Entry<RemoteInstance, ManagedChannel> e : this.managedChannels.entrySet()) {
			e.getValue().shutdown();
		}

		for (final Entry<RemoteInstance, ManagedChannel> e : this.managedChannels.entrySet()) {
			try {
				e.getValue().awaitTermination(30, TimeUnit.SECONDS);
			}
			catch (final InterruptedException e1) {
				// oh well we tried.
			}
		}
	}

	public List<RemoteInstance> getRemoteInstances() {
		return this.remoteInstances;
	}

	public MediaBlockingStub getMediaBlockingStub(final String rid) {
		final MediaBlockingStub stub = this.mediaBlockingStubs.get(rid);
		if (stub == null) throw new IllegalArgumentException("No stub found for: " + rid);
		return stub;
	}

	public MediaFutureStub getMediaFutureStub(final String rid) {
		final MediaFutureStub stub = this.mediaFutureStubs.get(rid);
		if (stub == null) throw new IllegalArgumentException("No stub found for: " + rid);
		return stub;
	}

	private void startChannel(final RemoteInstance ri) {
		final ManagedChannel channel = ri.getTarget().buildChannel();
		this.managedChannels.put(ri, channel);
		this.mediaFutureStubs.put(ri.getId(), MediaGrpc.newFutureStub(channel));
		this.mediaBlockingStubs.put(ri.getId(), MediaGrpc.newBlockingStub(channel));
	}

	private void parseArgs(final Args args) throws ArgsException {
		int id = 0;
		for (final String arg : args.getRemotes()) {
			this.remoteInstances.add(parseRemoteArg(arg, id));
			id += 1;
		}
	}

	private static RemoteInstance parseRemoteArg(final String arg, final int id) throws ArgsException {
		try {
			final RpcTarget target = RpcTarget.fromHttpUrl(arg);
			return new RemoteInstance(String.valueOf(id), target);
		}
		catch (final RpcConfigException e) {
			throw new ArgsException(e.getMessage());
		}
	}

}

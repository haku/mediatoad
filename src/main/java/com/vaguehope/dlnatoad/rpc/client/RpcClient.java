package com.vaguehope.dlnatoad.rpc.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.Args.ArgsException;
import com.vaguehope.dlnatoad.rpc.MediaGrpc;
import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaBlockingStub;
import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaFutureStub;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.TlsChannelCredentials;

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
		final ChannelCredentials channelCredentials;
		if (ri.isPlainText()) {
			channelCredentials = InsecureChannelCredentials.create();
		}
		else {
			channelCredentials = TlsChannelCredentials.create();
		}

		final ManagedChannelBuilder<?> channelBuilder = Grpc.newChannelBuilder(ri.getTarget(), channelCredentials);
//		if (ri.isPlainText()) channelBuilder.usePlaintext(); // TODO don't think we need this?

		final ManagedChannel channel = channelBuilder.build();
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

	private static RemoteInstance parseRemoteArg(final String arg, int id) throws ArgsException {
		final URI uri;
		try {
			uri = new URI(arg);
		}
		catch (final URISyntaxException e) {
			throw new Args.ArgsException("Invalid URI: " + arg);
		}

		if (StringUtils.isAllBlank(uri.getHost())) {
			throw new Args.ArgsException("Invalid host: " + arg);
		}

		int port;
		boolean plainText;
		if ("https".equals(uri.getScheme().toLowerCase())) {
			port = 443;
			plainText = false;
		}
		else if ("http".equals(uri.getScheme().toLowerCase())) {
			port = 80;
			plainText = true;
		}
		else {
			throw new Args.ArgsException("Invalid scheme: " + uri.getScheme());
		}
		if (uri.getPort() > 0) port = uri.getPort();

		final String target = String.format("dns:///%s:%s/", uri.getHost(), port);
		return new RemoteInstance(String.valueOf(id), target, plainText);
	}

}

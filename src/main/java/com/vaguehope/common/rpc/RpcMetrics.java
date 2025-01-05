package com.vaguehope.common.rpc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

public class RpcMetrics {

	// TODO use weak ref?
	private static final Map<String, ManagedChannel> CHANNELS = new ConcurrentSkipListMap<>();
	private static final Map<String, ClientRecorder> CLIENTS = new ConcurrentSkipListMap<>();

	public static void monitorChannel(final ManagedChannel channel, final String channelName) {
		if (CHANNELS.put(channelName, channel) != null) throw new IllegalArgumentException("Name already in use: " + channelName);
	}

	public static List<ChannelState> channelStates() {
		final List<ChannelState> ret = new ArrayList<>();
		for (final Iterator<Entry<String, ManagedChannel>> ittr = CHANNELS.entrySet().iterator(); ittr.hasNext();) {
			final Entry<String, ManagedChannel> entry = ittr.next();
			if (entry.getValue().isTerminated()) {
				ittr.remove();
			}
			else {
				ret.add(new ChannelState(entry.getKey(), entry.getValue().getState(false)));
			}
		}
		return ret;
	}

	public static Set<Entry<String, ClientRecorder>> clientMetrics() {
		return CLIENTS.entrySet();
	}

	public static ClientInterceptor clientInterceptor(final String clientName) {
		final ClientRecorder recorder = new ClientRecorder(clientName);
		if (CLIENTS.put(clientName, recorder) != null) throw new IllegalArgumentException("Name already in use: " + clientName);
		return new MetricInterceptor(recorder);
	}

	private static class MetricInterceptor implements ClientInterceptor {
		private final ClientRecorder recorder;

		public MetricInterceptor(final ClientRecorder recorder) {
			this.recorder = recorder;
		}

		@Override
		public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(final MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions, final Channel next) {
			return new MetricClientCall<>(this.recorder, method.getFullMethodName(), next.newCall(method, callOptions));
		}
	}

	private static class MetricClientCall<ReqT, RespT> extends SimpleForwardingClientCall<ReqT, RespT> {
		private final ClientRecorder recorder;
		private final String methodName;

		public MetricClientCall(final ClientRecorder recorder, final String methodName, final ClientCall<ReqT, RespT> call) {
			super(call);
			this.recorder = recorder;
			this.methodName = methodName;
		}

		@Override
		public void start(final Listener<RespT> responseListener, final Metadata headers) {
			super.start(new MetricListener(responseListener), headers);
		}

		private class MetricListener extends SimpleForwardingClientCallListener<RespT> {

			protected MetricListener(final Listener<RespT> delegate) {
				super(delegate);
			}

			@Override
			public void onClose(final Status status, final Metadata trailers) {
				MetricClientCall.this.recorder.recordRequestResult(MetricClientCall.this.methodName, status.getCode());
				super.onClose(status, trailers);
			}
		}
	}

	public static class ClientRecorder {

		private final String clientName;
		private final ConcurrentMap<String, MethodMetrics> methodMetrics = new ConcurrentHashMap<>();

		public ClientRecorder(final String clientName) {
			this.clientName = clientName;
		}

		public String getClientName() {
			return this.clientName;
		}

		public void recordRequestResult(final String methodName, final Status.Code status) {
			MethodMetrics mm = this.methodMetrics.get(methodName);
			if (mm == null) {
				mm = new MethodMetrics();
				final MethodMetrics prev = this.methodMetrics.putIfAbsent(methodName, mm);
				if (prev != null) mm = prev;
			}
			mm.recordStatus(status);
		}

		public Set<Entry<String, MethodMetrics>> methodAndMetrics() {
			return this.methodMetrics.entrySet();
		}
	}

	public static class MethodMetrics {
		private final ConcurrentMap<Status.Code, AtomicInteger> statusMetrics = new ConcurrentHashMap<>();

		public void recordStatus(final Status.Code status) {
			AtomicInteger ai = this.statusMetrics.get(status);
			if (ai == null) {
				ai = new AtomicInteger(0);
				final AtomicInteger prev = this.statusMetrics.putIfAbsent(status, ai);
				if (prev != null) ai = prev;
			}
			ai.incrementAndGet();
		}

		public Set<Entry<Status.Code, AtomicInteger>> statusAndCount() {
			return this.statusMetrics.entrySet();
		}
	}

	public static class ChannelState {

		private final String name;
		private final ConnectivityState state;

		public ChannelState(final String name, final ConnectivityState state) {
			this.name = name;
			this.state = state;
		}

		public String getName() {
			return this.name;
		}

		public ConnectivityState getState() {
			return this.state;
		}

		@Override
		public int hashCode() {
			return this.name.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == null) return false;
			if (this == obj) return true;
			if (!(obj instanceof ChannelState)) return false;
			final ChannelState that = (ChannelState) obj;
			return Objects.equals(this.name, that.name);
		}
	}

}

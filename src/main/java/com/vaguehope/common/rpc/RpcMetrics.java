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
import java.util.concurrent.TimeUnit;

import com.vaguehope.common.util.TotalOverTime;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class RpcMetrics {

	// TODO use weak ref?
	private static final Map<String, ManagedChannel> CHANNELS = new ConcurrentSkipListMap<>();
	private static final Map<String, EndpointRecorder> CLIENTS = new ConcurrentSkipListMap<>();
	private static final EndpointRecorder SERVER_RECORDER = new EndpointRecorder();

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

	public static Set<Entry<String, EndpointRecorder>> clientMetrics() {
		return CLIENTS.entrySet();
	}

	public static ClientInterceptor clientInterceptor(final String clientName) {
		final EndpointRecorder recorder = new EndpointRecorder();
		if (CLIENTS.put(clientName, recorder) != null) throw new IllegalArgumentException("Name already in use: " + clientName);
		return new ClientMetricInterceptor(recorder);
	}

	public static Set<Entry<String, MethodMetrics>> serverMethodAndMetrics() {
		return SERVER_RECORDER.methodAndMetrics();
	}

	public static ServerInterceptor serverInterceptor() {
		return new ServerMetricInterceptor(SERVER_RECORDER);
	}

	private static class ClientMetricInterceptor implements ClientInterceptor {
		private final EndpointRecorder recorder;

		public ClientMetricInterceptor(final EndpointRecorder recorder) {
			this.recorder = recorder;
		}

		@Override
		public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(final MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions, final Channel next) {
			return new MetricClientCall<>(this.recorder, method.getFullMethodName(), next.newCall(method, callOptions));
		}
	}

	private static class ServerMetricInterceptor implements ServerInterceptor {
		private final EndpointRecorder recorder;

		public ServerMetricInterceptor(final EndpointRecorder recorder) {
			this.recorder = recorder;
		}

		@Override
		public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
			MetricServerCall<ReqT, RespT> newCall = new MetricServerCall<>(this.recorder, call.getMethodDescriptor().getFullMethodName(), call);
			return next.startCall(newCall, headers);
		}
	}

	private static class MetricClientCall<ReqT, RespT> extends SimpleForwardingClientCall<ReqT, RespT> {
		private final EndpointRecorder recorder;
		private final String methodName;

		public MetricClientCall(final EndpointRecorder recorder, final String methodName, final ClientCall<ReqT, RespT> call) {
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

	private static class MetricServerCall<ReqT, RespT> extends SimpleForwardingServerCall<ReqT, RespT> {
		private final EndpointRecorder recorder;
		private final String methodName;

		public MetricServerCall(final EndpointRecorder recorder, final String methodName, final ServerCall<ReqT, RespT> delegate) {
			super(delegate);
			this.recorder = recorder;
			this.methodName = methodName;
		}

		@Override
		public void close(Status status, Metadata trailers) {
			this.recorder.recordRequestResult(this.methodName, status.getCode());
			super.close(status, trailers);
		}
	}

	public static class EndpointRecorder {

		private final ConcurrentMap<String, MethodMetrics> methodMetrics = new ConcurrentHashMap<>();

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
		private final ConcurrentMap<Status.Code, TimeSet> statusMetrics = new ConcurrentHashMap<>();

		public void recordStatus(final Status.Code status) {
			TimeSet ts = this.statusMetrics.get(status);
			if (ts == null) {
				ts = new TimeSet();
				final TimeSet prev = this.statusMetrics.putIfAbsent(status, ts);
				if (prev != null) ts = prev;
			}
			ts.increment();
		}

		public Set<Entry<Status.Code, TimeSet>> statusAndCount() {
			return this.statusMetrics.entrySet();
		}
	}

	public static class TimeSet {
		private final TotalOverTime fiveMin = new TotalOverTime(5, TimeUnit.MINUTES, 20);
		private final TotalOverTime oneHour = new TotalOverTime(1, TimeUnit.HOURS, 20);
		private final TotalOverTime oneDay = new TotalOverTime(1, TimeUnit.DAYS, 24);

		public void increment() {
			this.fiveMin.increment(1);
			this.oneHour.increment(1);
			this.oneDay.increment(1);
		}

		public long getFiveMin() {
			return this.fiveMin.get();
		}

		public long getOneHour() {
			return this.oneHour.get();
		}

		public long getOneDay() {
			return this.oneDay.get();
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

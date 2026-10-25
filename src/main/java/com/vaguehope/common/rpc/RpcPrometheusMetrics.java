package com.vaguehope.common.rpc;

import io.grpc.opentelemetry.GrpcOpenTelemetry;
import io.opentelemetry.exporter.prometheus.PrometheusMetricReader;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public class RpcPrometheusMetrics {

	@SuppressWarnings("resource")
	public static void setup() {
		PrometheusMetricReader prometheusMetricReader = PrometheusMetricReader.builder()
				.setOtelScopeLabelsEnabled(false)
				.setTargetInfoMetricEnabled(false)
				.build();
		PrometheusRegistry.defaultRegistry.register(prometheusMetricReader);
		SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
				.registerMetricReader(prometheusMetricReader)
				.build();
		OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
				.setMeterProvider(sdkMeterProvider)
				.build();
		// see io.grpc.opentelemetry.GrpcOpenTelemetry for metrics to enable.
		GrpcOpenTelemetry grpcOpenTelmetry = GrpcOpenTelemetry.newBuilder()
				.sdk(openTelemetrySdk)
				.build();
		grpcOpenTelmetry.registerGlobal();
	}

}

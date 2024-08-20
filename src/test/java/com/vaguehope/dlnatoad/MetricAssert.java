package com.vaguehope.dlnatoad;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.CounterSnapshot.CounterDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;

public class MetricAssert {

	private final MetricSnapshots beforeSnapshots;

	public MetricAssert() {
		this.beforeSnapshots = PrometheusRegistry.defaultRegistry.scrape();
	}

	public void assertCounter(final String name, final Labels labels, final long expectedValue) {
		final MetricSnapshot before = findMetricSnapshot(this.beforeSnapshots, name);
		final MetricSnapshot now = findMetricSnapshot(PrometheusRegistry.defaultRegistry.scrape(), name);

		final CounterDataPointSnapshot beforeDp = readCounter(before, labels);
		final CounterDataPointSnapshot nowDp = readCounter(now, labels);

		final double delta = nowDp.getValue() - beforeDp.getValue();
		assertEquals(expectedValue, delta, 0);
	}

	private static MetricSnapshot findMetricSnapshot(MetricSnapshots ss, final String name) {
		final List<MetricSnapshot> matches = ss.stream().filter(s -> name.equals(s.getMetadata().getName())).collect(Collectors.toList());
		if (matches.size() != 1) Assert.fail("metric '" + name + "' not found exactly once.");
		return matches.get(0);
	}

	private static CounterDataPointSnapshot readCounter(final MetricSnapshot ss, final Labels labels) {
		if (!(ss instanceof CounterSnapshot)) Assert.fail("metric is not a counter type.");
		final CounterSnapshot css = (CounterSnapshot) ss;

		final List<CounterDataPointSnapshot> dps = css.getDataPoints().stream().filter(d -> labels.equals(d.getLabels())).collect(Collectors.toList());
		if (dps.size() != 1) Assert.fail("Labels '" + labels + "' not found exactly once.");
		return dps.get(0);
	}

}

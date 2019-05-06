package com.vaguehope.dlnatoad.dlnaserver;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.vaguehope.dlnatoad.util.Time;

public class ContentServingHistory {

	private static final long MAX_AGE_NANOS = TimeUnit.HOURS.toNanos(1);

	private final Multiset<String> active = ConcurrentHashMultiset.create();
	private final Map<String, Long> lastActivity = new ConcurrentHashMap<>();
	private final Time time;

	public ContentServingHistory() {
		this.time = Time.DEFAULT;
	}

	public void recordStart(final String remoteAddr, final String requestURI) {
		this.active.add(remoteAddr);
		this.lastActivity.put(remoteAddr, this.time.now());
	}

	public void recordEnd(final String remoteAddr, final String requestURI) {
		this.active.remove(remoteAddr);
	}

	public int getActiveCount() {
		return this.active.entrySet().size();
	}

	public int getRecentlyActiveCount(final long durationSeconds) {
		long durationNanos = TimeUnit.SECONDS.toNanos(durationSeconds);
		int count = 0;
		final Iterator<Entry<String, Long>> itt = this.lastActivity.entrySet().iterator();
		final long now = this.time.now();
		while (itt.hasNext()) {
			final long ageNanos = now - itt.next().getValue();
			if (ageNanos < durationNanos) {
				count += 1;
			}
			else if (ageNanos > MAX_AGE_NANOS) {
				itt.remove();
			}

		}
		return count;
	}

}

package com.vaguehope.dlnatoad.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Ticker;

public class GenTimer {

	private final Ticker ticker;
	private final long firstStart;
	private final List<Section> sections = new ArrayList<>();

	private long sectionStart = -1;
	private String sectionName = null;

	public GenTimer() {
		this(Ticker.systemTicker());
	}

	protected GenTimer(final Ticker ticker) {
		this.ticker = ticker;
		this.firstStart = ticker.read();
	}

	public void startSection(final String name) {
		if (this.sectionStart >= 0) endSection();

		this.sectionStart = this.ticker.read();
		this.sectionName = name;
	}

	public void endSection() {
		if (this.sectionStart < 0) throw new IllegalStateException();

		this.sections.add(new Section(this.ticker.read() - this.sectionStart, this.sectionName));
		this.sectionStart = -1;
		this.sectionName = null;
	}

	public String summarise() {
		if (this.sectionStart >= 0) endSection();

		String s = TimeUnit.NANOSECONDS.toMillis(this.ticker.read() - this.firstStart) + "ms";
		for (Section section : this.sections) {
			s += " [" + section.name + ": " + TimeUnit.NANOSECONDS.toMillis(section.dur) + "ms]";
		}
		return s;
	}

	private static class Section {

		private final long dur;
		private final String name;

		public Section(final long start, final String name) {
			this.dur = start;
			this.name = name;
		}

	}

}

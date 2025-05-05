package com.vaguehope.dlnatoad.util;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.vaguehope.dlnatoad.FakeTicker;

public class GenTimerTest {

	private FakeTicker ticker;
	private GenTimer undertest;

	@Before
	public void before() throws Exception {
		this.ticker = new FakeTicker();
		this.undertest = new GenTimer(this.ticker);
	}

	@Test
	public void itDoesOverallDuration() throws Exception {
		this.ticker.addTime(13, TimeUnit.MILLISECONDS);
		assertEquals("13ms", this.undertest.summarise());
	}

	@Test
	public void itDoesSections() throws Exception {
		this.undertest.startSection("foo");
		this.ticker.addTime(5, TimeUnit.MILLISECONDS);

		this.undertest.startSection("bar");
		this.ticker.addTime(7, TimeUnit.MILLISECONDS);
		this.undertest.endSection();
		this.ticker.addTime(3, TimeUnit.MILLISECONDS);

		this.undertest.startSection("bat");
		this.ticker.addTime(13, TimeUnit.MILLISECONDS);

		assertEquals("28ms [foo: 5ms] [bar: 7ms] [bat: 13ms]", this.undertest.summarise());
	}

}

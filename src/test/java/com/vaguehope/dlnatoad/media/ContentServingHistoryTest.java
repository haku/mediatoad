package com.vaguehope.dlnatoad.media;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.vaguehope.dlnatoad.media.ContentServingHistory;

public class ContentServingHistoryTest {

	 private ContentServingHistory undertest;

	@Before
	public void before() throws Exception {
		this.undertest = new ContentServingHistory();
	}

	@Test
	public void itCountsActive() throws Exception {
		assertEquals(0, this.undertest.getActiveCount());
		this.undertest.recordStart("addr1", "/foo");
		assertEquals(1, this.undertest.getActiveCount());
		this.undertest.recordStart("addr1", "/foo");
		assertEquals(1, this.undertest.getActiveCount());
		this.undertest.recordStart("addr1", "/bat");
		assertEquals(1, this.undertest.getActiveCount());
		this.undertest.recordEnd("addr1", "/foo");
		assertEquals(1, this.undertest.getActiveCount());
		this.undertest.recordStart("addr2", "/bat");
		assertEquals(2, this.undertest.getActiveCount());
		this.undertest.recordEnd("addr1", "/bar");
		assertEquals(2, this.undertest.getActiveCount());
		this.undertest.recordEnd("addr1", "/foo");
		assertEquals(1, this.undertest.getActiveCount());
		this.undertest.recordEnd("addr2", "/bat");
		assertEquals(0, this.undertest.getActiveCount());
	}

	@Test
	public void itCountsRecent() throws Exception {
		assertEquals(0, this.undertest.getRecentlyActiveCount(1));

		this.undertest.recordStart("addr1", "/foo");
		assertEquals(1, this.undertest.getRecentlyActiveCount(1));

		this.undertest.recordStart("addr1", "/foo");
		assertEquals(1, this.undertest.getRecentlyActiveCount(1));

		this.undertest.recordStart("addr2", "/foo");
		assertEquals(2, this.undertest.getRecentlyActiveCount(1));

		this.undertest.recordStart("addr3", "/foo");
		assertEquals(3, this.undertest.getRecentlyActiveCount(1));
	}

}

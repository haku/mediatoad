package com.vaguehope.dlnatoad.db;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.vaguehope.dlnatoad.FakeTicker;

public class DbCacheTest {

	private MediaDb db;
	private FakeTicker ticker;
	private DbCache undertest;

	@Before
	public void before() throws Exception {
		this.db = mock(MediaDb.class);
		this.ticker = new FakeTicker();
		this.undertest = new DbCache(this.db, this.ticker);
	}

	@Test
	public void itRefreshesWhenDbChanges() throws Exception {
		final String pathPrefix = "/some/media/dir/path";

		final List<TagFrequency> ret1 = Arrays.asList(new TagFrequency("foo", 1));
		when(this.db.getTopTags(eq(null), eq(pathPrefix), anyInt())).thenReturn(ret1);
		when(this.db.getWriteCount()).thenReturn(1L);

		assertEquals(ret1, this.undertest.dirTopTags(null, pathPrefix));
		verify(this.db, times(1)).getTopTags(any(), any(), anyInt());

		this.ticker.addTime(1, TimeUnit.HOURS);
		assertEquals(ret1, this.undertest.dirTopTags(null, pathPrefix));
		verify(this.db, times(1)).getTopTags(any(), any(), anyInt());

		final List<TagFrequency> ret2 = Arrays.asList(new TagFrequency("bar", 1));
		when(this.db.getTopTags(eq(null), eq(pathPrefix), anyInt())).thenReturn(ret2);
		when(this.db.getWriteCount()).thenReturn(2L);

		this.ticker.addTime(10, TimeUnit.MINUTES);
		assertEquals(ret2, this.undertest.dirTopTags(null, pathPrefix));
		verify(this.db, times(2)).getTopTags(any(), any(), anyInt());
	}

}

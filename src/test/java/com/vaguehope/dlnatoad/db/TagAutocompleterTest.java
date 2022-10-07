package com.vaguehope.dlnatoad.db;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import com.vaguehope.dlnatoad.db.TagAutocompleter.FragmentAndTag;

public class TagAutocompleterTest {

	@Test
	public void itMakesFragments() throws Exception {
		final List<FragmentAndTag> actual = TagAutocompleter.makeFragments("foobar", 10);
		assertEquals(actual, Arrays.asList(
				new FragmentAndTag("oobar", "foobar", 10),
				new FragmentAndTag("obar", "foobar", 10),
				new FragmentAndTag("bar", "foobar", 10),
				new FragmentAndTag("ar", "foobar", 10),
				new FragmentAndTag("r", "foobar", 10)
				));
	}

	@Ignore
	@Test
	public void itLoadsRealDbData() throws Exception {
		final File dbFile = new File(new File(System.getProperty("user.home")), "tmp/dlnatoad-db");
		final MediaDb mediaDb = new MediaDb(dbFile);
		final TagAutocompleter undertest = new TagAutocompleter(mediaDb);
		undertest.generateIndex();

		final long startNanos = System.nanoTime();
//		final List<TagFrequency> res = undertest.suggestTags("a");
		final List<TagFrequency> res = undertest.suggestFragments("i");
		final long endNanos = System.nanoTime();
		System.out.println("Search took: " + TimeUnit.MICROSECONDS.toMillis(endNanos - startNanos) + " microseconds");
		System.out.println("Result count: " + res.size());

		for (final TagFrequency t : res) {
			System.out.println(t.getTag() + " (" + t.getCount() + ")");
		}
	}

}

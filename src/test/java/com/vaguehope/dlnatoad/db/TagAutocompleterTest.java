package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.db.MockMediaMetadataStore.Batch;
import com.vaguehope.dlnatoad.db.TagAutocompleter.FragmentAndTag;

public class TagAutocompleterTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private MockMediaMetadataStore mockMediaMetadataStore;
	private MediaDb mediaDb;
	private TagAutocompleter undertest;

	@Before
	public void before() throws Exception {
		this.mockMediaMetadataStore = MockMediaMetadataStore.withRealExSvc(this.tmp);
		this.mediaDb = this.mockMediaMetadataStore.getMediaDb();
		this.undertest = new TagAutocompleter(this.mediaDb);
	}

	@Test
	public void itMakesFragments() throws Exception {
		final List<FragmentAndTag> actual = TagAutocompleter.makeFragments("foobar", 10);
		assertThat(actual, contains(
				new FragmentAndTag("oobar", "foobar", 10),
				new FragmentAndTag("obar", "foobar", 10),
				new FragmentAndTag("bar", "foobar", 10),
				new FragmentAndTag("ar", "foobar", 10),
				new FragmentAndTag("r", "foobar", 10)
				));
	}

	@Test
	public void itSuggestsTagPrefixMatches() throws Exception {
		mockFilesWithTags();
		this.undertest.generateIndex();

		assertThat(this.undertest.suggestTags("zzzzzzzzzzzzzzzzzzz"), empty());

		final List<TagFrequency> actual = this.undertest.suggestTags("foo");
		assertEquals(Arrays.asList(
				new TagFrequency("fooa", 25),
				new TagFrequency("fooob", 24),
				new TagFrequency("fooc", 23),
				new TagFrequency("foood", 22),
				new TagFrequency("fooe", 21),
				new TagFrequency("fooof", 20),
				new TagFrequency("foog", 19),
				new TagFrequency("foooh", 18),
				new TagFrequency("fooi", 17),
				new TagFrequency("foooj", 16),
				new TagFrequency("fook", 15),
				new TagFrequency("foool", 14),
				new TagFrequency("foom", 13),
				new TagFrequency("fooon", 12),
				new TagFrequency("fooo", 11),
				new TagFrequency("fooop", 10),
				new TagFrequency("fooq", 9),
				new TagFrequency("fooor", 8),
				new TagFrequency("foos", 7),
				new TagFrequency("fooot", 6)
				), actual);
	}

	@Test
	public void itSuggestsTagFragmentMatches() throws Exception {
		mockFilesWithTags();
		this.undertest.generateIndex();

		assertThat(this.undertest.suggestFragments("foo"), empty());

		final List<TagFrequency> actual = this.undertest.suggestFragments("oo");
		assertEquals(Arrays.asList(
				new TagFrequency("aooa", 25),
				new TagFrequency("booa", 25),
				new TagFrequency("cooa", 25),
				new TagFrequency("dooa", 25),
				new TagFrequency("eooa", 25),
				new TagFrequency("fooa", 25),
				new TagFrequency("gooa", 25),
				new TagFrequency("hooa", 25),
				new TagFrequency("iooa", 25),
				new TagFrequency("jooa", 25),
				new TagFrequency("kooa", 25),
				new TagFrequency("looa", 25),
				new TagFrequency("mooa", 25),
				new TagFrequency("nooa", 25),
				new TagFrequency("oooa", 25),
				new TagFrequency("pooa", 25),
				new TagFrequency("qooa", 25),
				new TagFrequency("rooa", 25),
				new TagFrequency("sooa", 25),
				new TagFrequency("tooa", 25)
				), actual);
	}

	private void mockFilesWithTags() throws IOException, InterruptedException, Exception {
		try (final Batch b = this.mockMediaMetadataStore.batch()) {
			for (char x = 'a'; x <= 'z'; x++) {
				for (char y = 'a'; y <= 'z'; y++) {
					for (int i = 0; i < 'z' - y; i++) {
						b.fileWithTags(x + "oo" + (y % 2 == 0 ? "o" : "") + y);
					}
				}
			}
		}
	}

	@Ignore
	@Test
	public void itLoadsRealDbData() throws Exception {
		final File dbFile = new File(new File(System.getProperty("user.home")), "tmp/dlnatoad-db");
		final MediaDb db = new MediaDb(dbFile);
		final TagAutocompleter ut = new TagAutocompleter(db);
		ut.generateIndex();

		final long startNanos = System.nanoTime();
//		final List<TagFrequency> res = ut.suggestTags("a");
		final List<TagFrequency> res = ut.suggestFragments("e");
		final long endNanos = System.nanoTime();
		System.out.println("Search took: "
				+ TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos) + " millis = "
				+ TimeUnit.NANOSECONDS.toMicros(endNanos - startNanos) + " micros");
		System.out.println("Result count: " + res.size());

		for (final TagFrequency t : res) {
			System.out.println(t.getTag() + " (" + t.getCount() + ")");
		}
	}

}

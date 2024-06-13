package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
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

	private ScheduledExecutorService schEx;
	private MockMediaMetadataStore mockMediaMetadataStore;
	private MediaDb mediaDb;
	private TagAutocompleter undertest;

	@Before
	public void before() throws Exception {
		this.schEx = mock(ScheduledExecutorService.class);
		doAnswer(inv -> {
				inv.getArgument(0, Runnable.class).run();
				return null;
			}).when(this.schEx).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
		doAnswer(inv -> {
			inv.getArgument(0, Runnable.class).run();
			return null;
		}).when(this.schEx).execute(any(Runnable.class));

		this.mockMediaMetadataStore = MockMediaMetadataStore.withRealExSvc(this.tmp);
		this.mediaDb = this.mockMediaMetadataStore.getMediaDb();
		this.undertest = new TagAutocompleter(this.mediaDb, this.schEx);
	}

	@Test
	public void itMakesFragments() throws Exception {
		final List<FragmentAndTag> actual = TagAutocompleter.makeFragments("foobar", "foobar", 10);
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

	@Test
	public void itSuggestsTagMatchesWithUpperCaseAndAccents() throws Exception {
		try (final Batch b = this.mockMediaMetadataStore.batch()) {
			b.fileWithTags("bar");
			b.fileWithTags("bat");
			b.fileWithTags("foo");
			b.fileWithTags("Blåhaj");
		}
		this.undertest.generateIndex();

		assertEquals(Arrays.asList(new TagFrequency("Blåhaj", 1)), this.undertest.suggestTags("Blå"));
		assertEquals(Arrays.asList(new TagFrequency("Blåhaj", 1)), this.undertest.suggestTags("Bla"));
		assertEquals(Arrays.asList(new TagFrequency("Blåhaj", 1)), this.undertest.suggestTags("bla"));
		assertEquals(Arrays.asList(new TagFrequency("Blåhaj", 1)), this.undertest.suggestTags("bl"));

		assertEquals(Arrays.asList(new TagFrequency("Blåhaj", 1)), this.undertest.suggestFragments("låh"));
		assertEquals(Arrays.asList(new TagFrequency("Blåhaj", 1)), this.undertest.suggestFragments("lah"));
	}

	@Test
	public void itIncrementsTagCount() throws Exception {
		try (final Batch b = this.mockMediaMetadataStore.batch()) {
			for (char x = 'a'; x <= 'z'; x++) {
				b.fileWithTags("" + x + x);
			}
			b.fileWithTags("12");
			b.fileWithTags("123923");
			b.fileWithTags("23");
		}

		this.undertest.generateIndex();
		assertEquals(Arrays.asList(new TagFrequency("ff", 1)), this.undertest.suggestTags("ff"));
		assertEquals(Arrays.asList(new TagFrequency("ff", 1)), this.undertest.suggestFragments("f"));

		// increment existing.
		this.undertest.addOrIncrementTag("ff");
		assertEquals(Arrays.asList(new TagFrequency("ff", 2)), this.undertest.suggestTags("ff"));
		assertEquals(Arrays.asList(new TagFrequency("ff", 2)), this.undertest.suggestFragments("f"));

		// insert at start.
		this.undertest.addOrIncrementTag("a");
		assertEquals(Arrays.asList(new TagFrequency("a", 1), new TagFrequency("aa", 1)), this.undertest.suggestTags("a"));
		assertEquals(Arrays.asList(new TagFrequency("aa", 1)), this.undertest.suggestFragments("a"));

		// insert in middle.
		this.undertest.addOrIncrementTag("fa");
		assertEquals(Arrays.asList(new TagFrequency("fa", 1)), this.undertest.suggestTags("fa"));
		assertEquals(Arrays.asList(new TagFrequency("ff", 2)), this.undertest.suggestFragments("f"));

		// insert but prefix is an existing entry.
		this.undertest.addOrIncrementTag("fff");
		assertEquals(Arrays.asList(new TagFrequency("fff", 1)), this.undertest.suggestTags("fff"));
		assertEquals(Arrays.asList(new TagFrequency("ff", 2)), this.undertest.suggestFragments("f"));

		// insert at end.
		this.undertest.addOrIncrementTag("zzz");
		assertEquals(Arrays.asList(new TagFrequency("zzz", 1)), this.undertest.suggestTags("zzz"));
		assertEquals(Arrays.asList(new TagFrequency("zz", 1)), this.undertest.suggestFragments("z"));

		// more complex, importantly "23" is repeated creating 2 fragments with the same prefix.
		assertEquals(Arrays.asList(new TagFrequency("123923", 1)), this.undertest.suggestFragments("9"));
		this.undertest.addOrIncrementTag("123923");
		assertEquals(Arrays.asList(new TagFrequency("123923", 2), new TagFrequency("12", 1)), this.undertest.suggestTags("1"));
		assertEquals(Arrays.asList(new TagFrequency("123923", 2), new TagFrequency("12", 1)), this.undertest.suggestFragments("2"));
		assertEquals(Arrays.asList(new TagFrequency("123923", 2), new TagFrequency("23", 1)), this.undertest.suggestFragments("3"));
		assertEquals(Arrays.asList(new TagFrequency("123923", 2)), this.undertest.suggestFragments("9"));

		// decrement existing.
		this.undertest.decrementTag("gg");
		assertEquals(Arrays.asList(new TagFrequency("gg", 0)), this.undertest.suggestTags("gg"));
		assertEquals(Arrays.asList(new TagFrequency("gg", 0)), this.undertest.suggestFragments("g"));
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
		final TagAutocompleter ut = new TagAutocompleter(db, this.schEx);
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

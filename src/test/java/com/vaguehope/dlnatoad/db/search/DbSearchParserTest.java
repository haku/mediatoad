package com.vaguehope.dlnatoad.db.search;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableSet;
import com.vaguehope.dlnatoad.db.FileInfo;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.MockMediaMetadataStore;
import com.vaguehope.dlnatoad.db.TagFrequency;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser.DbSearch;

public class DbSearchParserTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private MediaDb mediaDb;
	private MockMediaMetadataStore mockMediaMetadataStore;

	@Before
	public void before() throws Exception {
		this.mockMediaMetadataStore = MockMediaMetadataStore.withMockExSvc(this.tmp);
		this.mediaDb = this.mockMediaMetadataStore.getMediaDb();
		this.mockMediaMetadataStore.addNoiseToDb();
	}

	@Test
	public void itParsesSingleTermQuery() throws Exception {
		runParser("hello",
				"SELECT id FROM files INNER JOIN hashes USING (id) WHERE" +
				" missing=0" +
				" AND auth IN ('0')" +
				" AND (  (file LIKE ? ESCAPE ? OR id IN (SELECT file_id FROM tags WHERE tag LIKE ? ESCAPE ? AND deleted=0)) ) " +
				" ORDER BY file COLLATE NOCASE ASC",
				"hello");
	}

	@Test
	public void itSearchesWithSingleTermQuery() throws Exception {
		final String id = this.mockMediaMetadataStore.addFileWithTags("hello", "how", "are", "you");
		this.mockMediaMetadataStore.addMissingFileWithTags("hello");
		runQuery("hello", id);
	}

	@Test
	public void itPagesSearchResults() throws Exception {
		final List<String> ids = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			ids.add(this.mockMediaMetadataStore.addFileWithNameAndTags(String.format("file%07d", i), "thing" + i));
		}

		final DbSearch parsed = DbSearchParser.parseSearch("t~^thing", null, SortOrder.FILE.asc());

		final List<String> page0 = parsed.execute(this.mediaDb, 50, 0);
		assertThat(page0, contains(ids.subList(0, 50).toArray(new String[] {})));

		final List<String> page1 = parsed.execute(this.mediaDb, 50, 50);
		assertThat(page1, contains(ids.subList(50, 60).toArray(new String[] {})));
	}

	@Test
	public void itSeachesMatchingAuth() throws Exception {
		final String noauth = this.mockMediaMetadataStore.addFileWithAuthAndTags(BigInteger.ZERO, "hello");
		final String allowed = this.mockMediaMetadataStore.addFileWithAuthAndTags(BigInteger.valueOf(100001L), "hello");
		this.mockMediaMetadataStore.addFileWithAuthAndTags(BigInteger.valueOf(200002L), "hello");
		runQuery("hello", ImmutableSet.of(BigInteger.valueOf(100001L)), noauth, allowed);
	}

	@Test
	public void itSearchesByType() throws Exception {
		final String jpg = mockMediaTrackWithNameContaining("my photo", ".jpg");
		final String jpeg = mockMediaTrackWithNameContaining("another photo", ".jpeg");
		final String gif = mockMediaTrackWithNameContaining("thing", ".gif");
		final String mp4 = mockMediaTrackWithNameContaining("thing", ".mp4");
		final String avi = mockMediaTrackWithNameContaining("thing", ".avi");
		final String mp3 = mockMediaTrackWithNameContaining("thing", ".mp3");
		final String wav = mockMediaTrackWithNameContaining("thing", ".wav");

		runQuery("type=image", jpg, jpeg, gif);
		runQuery("type=video", mp4, avi);
		runQuery("type=audio", mp3, wav);

		runQuery("type=image/jpeg", jpg, jpeg);
		runQuery("type=video/mp4", mp4);
		runQuery("type=audio/mpeg", mp3);
	}

	@Test
	public void itSearchesForSingleItemByNameAndTag() throws Exception {
		final String term1 = "foobar";
		final String term2 = "desu";
		this.mockMediaMetadataStore.addFileWithTags("watcha", term2);
		this.mockMediaMetadataStore.addFileWithTags(term1, "something");

		final String expected = this.mockMediaMetadataStore.addFileWithTags(term1, term2);
		runQuery(term1 + " " + term2, expected);
		runQuery(term1 + "\t" + term2, expected);
		runQuery(term1 + "　" + term2, expected); // Ideographic Space.
	}

	@Test
	public void itSearchesForItemsByNameOrTag () throws Exception {
		final String term = "some_awesome_band_desu";
		final String expectedWithName = mockMediaTrackWithNameContaining(term);
		final String expectedWithTag = mockMediaFileWithTags("watcha " + term + " noise");
		runQuery(term, expectedWithName, expectedWithTag);
	}

	@Test
	public void itSearchesWithoutCrashingWhenStartingWithOr () throws Exception {
		final String mediaNameFragment = "OR some_media_file_" + System.nanoTime();
		final String expected = mockMediaTrackWithNameContaining(mediaNameFragment);
		runQuery(mediaNameFragment, expected);
	}

	@Test
	public void itSearchesWithoutCrashingWhenEndingWithOr () throws Exception {
		final String mediaNameFragment = "some_media_file_" + System.nanoTime();
		final String expected = mockMediaTrackWithNameContaining(mediaNameFragment);
		runQuery(mediaNameFragment + " OR", expected);
	}

	@Test
	public void itSearchesUsingMultipleTermsForItemsByNameOrTagUsingOrKeyword () throws Exception {
		final String term1 = "some_awesome_band_desu";
		final String term2 = "some_other_thing";
		final String expectedWithTerm1InName = mockMediaTrackWithNameContaining(term1);
		final String expectedWithTerm2InName = mockMediaTrackWithNameContaining(term2);
		final String expectedWithTerm1InTag = mockMediaFileWithTags("watcha " + term1 + " noise");
		final String expectedWithTerm2InTag = mockMediaFileWithTags("foo " + term2 + " bar");

		final String[] queries = new String[] {
				"  " + term1 + " OR " + term2 + " ",
				"  " + term1 + " OR OR " + term2 + " ",
		};
		for (final String q : queries) {
			runQuery(q, expectedWithTerm1InName, expectedWithTerm2InName, expectedWithTerm1InTag, expectedWithTerm2InTag);
		}
	}

	@Test
	public void itSearchesForJustPartialMatchFileName () throws Exception {
		final String term = "some_awesome_band_desu";
		final String expectedWithTermInName = mockMediaTrackWithNameContaining(term);
		mockMediaFileWithTags("watcha " + term + " noise");

		runQuery("f~" + term, expectedWithTermInName);
	}

	@Test
	public void itSearchesForJustPartialMatchFileNameUcaseType () throws Exception {
		final String term = "some_awesome_band_desu";
		final String expectedWithTermInName = mockMediaTrackWithNameContaining(term);
		mockMediaFileWithTags("watcha " + term + " noise");

		runQuery("F~" + term, expectedWithTermInName);
	}

	@Test
	public void itSearchesForMatchFileNameWithSpecialChars () throws Exception {
		final String term = "awesome'\"*%_\\)(band";

		// Noise:
		mockMediaFileWithTags("watcha " + term + " noise");

		final String expected = mockMediaTrackWithNameContaining(term);
		runQuery("f~" + term, expected);
	}

	@Test
	public void itSearchesForMatchTagWithSpecialChars () throws Exception {
		final String tag = "awesome'\"*%_\\)(band";

		// Noise:
		mockMediaFileWithTags("watcha " + tag + " noise");
		mockMediaFileWithTags("watcha awesome'\"*%_\\", ")(band noise");
		mockMediaFileWithTags("watcha awesome'\"*%_\\)", "(band noise");
		mockMediaFileWithTags("watcha awesome'\"*%_\\)(", "band noise");
		mockMediaFileWithTags("watcha awesome", "'\"*%_\\)(band noise");
		mockMediaFileWithTags("watcha awesome'", "\"*%_\\)(band noise");
		mockMediaFileWithTags("watcha awesome'\"", "*%_\\)(band noise");

		final String expected = mockMediaFileWithTags(tag);
		runQuery("t=" + tag, expected);
	}

	@Test
	public void itSearchesForJustPartialMatchFileNameEndAnchored () throws Exception {
		final String expectedWithTermInName = mockMediaTrackWithNameContaining("/foo/thing/bip bop bar", ".myext");
		mockMediaFileWithTags("watcha foo.myext noise");

		runQuery("f~.myext$", expectedWithTermInName);
	}

	@Test
	public void itSearchesForJustPartialMatchTag () throws Exception {
		final String term = "some_awesome_band_desu";
		mockMediaTrackWithNameContaining(term);
		final String expectedWithTermInTag = mockMediaFileWithTags("watcha " + term + " noise");

		runQuery("t~" + term, expectedWithTermInTag);
	}

	@Test
	public void itSearchesForJustPartialMatchTagUcaseType () throws Exception {
		final String term = "some_awesome_band_desu";
		mockMediaTrackWithNameContaining(term);
		final String expectedWithTermInTag = mockMediaFileWithTags("watcha " + term + " noise");

		runQuery("T~" + term, expectedWithTermInTag);
	}

	@Test
	public void itSearchesForJustPartialMatchTagStartAnchored () throws Exception {
		final String term = "some_awesome_band_desu";
		mockMediaTrackWithNameContaining(term);
		final String expectedWithTermInTag = mockMediaFileWithTags(term + " watcha");
		mockMediaFileWithTags("watcha " + term + " noise");

		runQuery("t~^" + term, expectedWithTermInTag);
	}

	@Test
	public void itSearchesForJustPartialMatchTagEndAnchored () throws Exception {
		final String term = "some_awesome_band_desu";
		mockMediaTrackWithNameContaining(term);
		final String expectedWithTermInTag = mockMediaFileWithTags("watcha " + term);
		mockMediaFileWithTags("watcha " + term + " noise");

		runQuery("t~" + term + "$", expectedWithTermInTag);
	}

	@Test
	public void itSearchesForJustExactlyMatchTag () throws Exception {
		final String term = "pl_desu";
		final String expectedWithTermAsTag = mockMediaFileWithTags(term);
		mockMediaFileWithTags("watcha " + term + " noise");

		runQuery("t=" + term, expectedWithTermAsTag);
	}

	@Test
	public void itSearchesForJustExactlyMatchTagUcaseType () throws Exception {
		final String term = "pl_desu";
		final String expectedWithTermAsTag = mockMediaFileWithTags(term);
		mockMediaFileWithTags("watcha " + term + " noise");

		runQuery("T=" + term, expectedWithTermAsTag);
	}

	@Test
	public void itSearchesForSingleItemByNameAndTagSpecifically () throws Exception {
		final String term1 = "foobar";
		final String term2 = "desu";
		mockMediaFileWithNameFragmentAndTags("watcha", term2);
		mockMediaFileWithNameFragmentAndTags(term1, "something");
		final String expected = mockMediaFileWithNameFragmentAndTags(term1, term2);

		runQuery("f~" + term1 + " t=" + term2, expected);
	}

	@Test
	public void itSearchesForItemsThatMatchTwoTags () throws Exception {
		final String expectedWithTags = mockMediaFileWithTags(
				"some_awesome_band_desu",
				"happy_track_nyan~"
				);

		runQuery("some_awesome_band_desu happy_track_nyan~", expectedWithTags);
	}

	@Test
	public void itSearchesForItemsThatMatchTwoExplicitTags () throws Exception {
		final String expectedWithTags = mockMediaFileWithTags(
				"some_awesome_band_desu",
				"happy_track_nyan~");

		runQuery("t=some_awesome_band_desu t=happy_track_nyan~", expectedWithTags);
	}

	@Test
	public void itFindsDuplicates() throws Exception {
		final byte[] a = MockMediaMetadataStore.randomBytes();
		final String a0 = this.mockMediaMetadataStore.addFileWithContent(a);
		final String a1 = this.mockMediaMetadataStore.addFileWithContent(a);
		final String a3 = this.mockMediaMetadataStore.addFileWithContent(a);
		assertEquals(a0, a1);
		assertEquals(a0, a3);

		final byte[] b = MockMediaMetadataStore.randomBytes();
		final String b0 = this.mockMediaMetadataStore.addFileWithContent(b);
		final String b1 = this.mockMediaMetadataStore.addFileWithContent(b);
		assertEquals(b0, b1);
		assertNotEquals(a0, b0);

		runQuery("dupes>0", a0, b0);
		runQuery("dupes>1", a0);
	}

	@Test
	public void itFindsDuplicatesHonouringAuth() throws Exception {
		final BigInteger user0 = BigInteger.valueOf(100001L);
		final BigInteger user1 = BigInteger.valueOf(100002L);

		final byte[] a = MockMediaMetadataStore.randomBytes();
		final String a0 = this.mockMediaMetadataStore.addFileWithContentAndAuth(a, BigInteger.ZERO);
		final String a1 = this.mockMediaMetadataStore.addFileWithContentAndAuth(a, BigInteger.ZERO);
		final String a2 = this.mockMediaMetadataStore.addFileWithContentAndAuth(a, user0);
		final String a3 = this.mockMediaMetadataStore.addFileWithContentAndAuth(a, user1);
		final String a4 = this.mockMediaMetadataStore.addFileWithContentAndAuth(a, user1);
		assertEquals(a0, a1);
		assertEquals(a0, a2);
		assertEquals(a0, a3);
		assertEquals(a0, a4);

		runQuery("dupes>0", a0);
		runQuery("dupes>1");

		runQuery("dupes>1", ImmutableSet.of(user0), a0);
		runQuery("dupes>2", ImmutableSet.of(user0));

		runQuery("dupes>2", ImmutableSet.of(user1), a0);
		runQuery("dupes>3", ImmutableSet.of(user1));
	}

	@Test
	public void itSearchesForPartialMatchFileNameQuoted () throws Exception {
		final String term = "some awesome? band desu";

		final String term1 = term.replace('?', '"');
		final String expected1 = mockMediaTrackWithNameContaining(term1);
		runQuery("'" + term1 + "'", expected1);

		final String term2 = term.replace('?', '\'');
		final String expected2 = mockMediaTrackWithNameContaining(term2);
		runQuery("\"" + term2 + "\"", expected2);
	}

	@Test
	public void itSearchesForJustPartialMatchFileNameQuoted () throws Exception {
		final String term = "some awesome? band desu";
		mockMediaFileWithTags("watcha " + term + " noise");

		final String term1 = term.replace('?', '"');
		final String expected1 = mockMediaTrackWithNameContaining(term1);
		runQuery("f~'" + term1 + "'", expected1);

		final String term2 = term.replace('?', '\'');
		final String expected2 = mockMediaTrackWithNameContaining(term2);
		runQuery("f~\"" + term2 + "\"", expected2);
	}

	@Test
	public void itSearchesPathWithEscapedSingleQuotes () throws Exception {
		final String path = "some \"awesome' band\" desu";
		final String search = "f~'some \"awesome\\' band\" desu'";

		final String expected = mockMediaTrackWithNameContaining(path);
		runQuery(search, expected);
	}

	@Test
	public void itSearchesPathWithEscapedDoubleQuotes () throws Exception {
		final String path = "some 'awesome\" band' desu";
		final String search = "f~\"some 'awesome\\\" band' desu\"";

		final String expected = mockMediaTrackWithNameContaining(path);
		runQuery(search, expected);
	}

	@Test
	public void itSearchesTagWithEscapedSingleQuote () throws Exception {
		final String tag = "some media' tag ";
		final String search = "t=some' media\\' tag '";

		final String expected = mockMediaFileWithTags(tag);
		runQuery(search, expected);
	}

	@Test
	public void itSearchesTagWithEscapedSingleQuote2 () throws Exception {
		final String tag = "some \\\"media' tag ";
		final String search = "t=some' \\\"media\\' tag '";

		final String expected = mockMediaFileWithTags(tag);
		runQuery(search, expected);
	}

	@Test
	public void itSearchesTagWithEscapedDoubleQuote () throws Exception {
		final String tag = "some media\" tag ";
		final String search = "t=some\" media\\\" tag \"";

		final String expected = mockMediaFileWithTags(tag);
		runQuery(search, expected);
	}

	@Test
	public void itSearchesTagWithEscapedDoubleQuote2 () throws Exception {
		final String tag = "some \\'media\" tag ";
		final String search = "t=some\" \\'media\\\" tag \"";

		final String expected = mockMediaFileWithTags(tag);
		runQuery(search, expected);
	}

	@Test
	public void itSearchesUsingMultipleTermsAndBrackets () throws Exception {
		final String expected1 = mockMediaFileWithNameFragmentAndTags("some_folder", "foo");
		final String expected2 = mockMediaFileWithNameFragmentAndTags("some_folder", "bar");

		// Noise:
		mockMediaFileWithNameFragmentAndTags("other_folder", "foo");
		mockMediaFileWithNameFragmentAndTags("other_folder", "bar");

		// Verify:
		runQuery("t=foo f~some_folder", expected1);
		runQuery("t=bar f~some_folder", expected2);

		final String[] queries = new String[] {
				"(t=bar OR t=foo) f~some_folder",
				"f~some_folder (t=bar OR t=foo)",
				"f~some_folder AND (t=bar OR t=foo)",
				"f~some_folder AND AND (t=bar OR t=foo)",
		};
		for (final String q : queries) {
			runQuery(q, expected1, expected2);
		}
	}

	@Test
	public void itSearchesWithoutCrashingWithUnbalancedOpenBracket () throws Exception {
		final String mediaNameFragment = "some_media_file_" + System.nanoTime();
		final String expected = mockMediaTrackWithNameContaining(mediaNameFragment);
		runQuery("(" + mediaNameFragment, expected);
	}

	@Test
	public void itSearchesWithoutCrashingWithUnbalancedOpenBracketAndLeadingOr () throws Exception {
		final String mediaNameFragment = "some_media_file_" + System.nanoTime();
		final String expected = mockMediaTrackWithNameContaining(mediaNameFragment);
		runQuery("( OR " + mediaNameFragment, expected);
	}

	@Test
	public void itSearchesWithoutCrashingWithUnbalancedCloseBracket () throws Exception {
		final String mediaNameFragment = "some_media_file_" + System.nanoTime();
		final String expected = mockMediaTrackWithNameContaining(mediaNameFragment);
		runQuery(mediaNameFragment + ")", expected);
	}

	@Test
	public void itSearchesWithoutCrashingWithUnbalancedCloseBracketAndTrailingOr () throws Exception {
		final String mediaNameFragment = "some_media_file_" + System.nanoTime();
		final String expected = mockMediaTrackWithNameContaining(mediaNameFragment);
		runQuery(mediaNameFragment + " OR )", expected);
	}

	@Test
	public void itSearchesForNegativeTagExactMatch () throws Exception {
		mockMediaFileWithTags("abc", "foobar");
		final String nonMatch = mockMediaFileWithTags("abc", "desu");
		runQuery("t=abc -t=foobar", nonMatch);
	}

	@Test
	public void itSearchesForNegativeTagPartialMatch () throws Exception {
		mockMediaFileWithTags("abc", "foobar");
		final String nonMatch = mockMediaFileWithTags("abc", "desu");
		runQuery("t=abc -t~foo", nonMatch);
	}

	@Test
	public void itSearchesForNegativeFilePartialMatch () throws Exception {
		mockMediaFileWithNameFragmentAndTags("foobar", "abc");
		final String nonMatch = mockMediaFileWithTags("abc");
		runQuery("t=abc -f~foo", nonMatch);
	}

	@Test
	public void itMatchesItemWithNoTags() throws Exception {
		final String notag0 = mockMediaTrackWithNameContaining("somepath");
		final String notag1 = mockMediaTrackWithNameContaining("otherpath");
		mockMediaFileWithTags("abc", "desu");
		mockMediaFileWithTags("def", "hello");
		runQuery("t<1", notag0, notag1);
	}

	@Test
	public void itMatchesItemWith1OrLessTags() throws Exception {
		final String notag0 = mockMediaTrackWithNameContaining("somepath");
		final String notag1 = mockMediaTrackWithNameContaining("otherpath");
		final String onetag0 = mockMediaFileWithTags("abc");
		final String onetag1 = mockMediaFileWithTags("def");
		mockMediaFileWithTags("abc", "desu");
		mockMediaFileWithTags("def", "hello");
		runQuery("t<2", notag0, notag1, onetag0, onetag1);
	}

	@Test
	public void itMatchesItemWithManyTags() throws Exception {
		mockMediaTrackWithNameContaining("somepath");
		mockMediaTrackWithNameContaining("otherpath");

		final String t1 = mockMediaFileWithTags("one");
		final String t2 = mockMediaFileWithTags("one", "two");
		final String t3 = mockMediaFileWithTags("one", "two", "three");
		final String t4 = mockMediaFileWithTags("one", "two", "three", "four");
		final String t5 = mockMediaFileWithTags("one", "two", "three", "four", "five");

		runQuery("t=one t>0", t1, t2, t3, t4, t5);
		runQuery("t=one t>1", t2, t3, t4, t5);
		runQuery("t=one t>2", t3, t4, t5);
		runQuery("t=one t>3", t4, t5);
		runQuery("t=one t>4", t5);
	}

	@Test
	public void itDoesNotDoubleCountSameTagWithDifferentClasses() throws Exception {
		final String t4 = mockMediaFileWithTags("one", "two", "three", "four");
		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			w.addTag(t4, "one", "class1", System.currentTimeMillis());
			w.addTag(t4, "one", "class2", System.currentTimeMillis());
			w.addTag(t4, "one", "class3", System.currentTimeMillis());
		}
		final String t5 = mockMediaFileWithTags("one", "two", "three", "four", "five");
		runQuery("t=one t<5", t4);
		runQuery("t=one t>4", t5);
	}

	@Test
	public void itDoesNotCrashWithBadQuery() throws Exception {
		final String term = "t=\"a very long tag that does not fit on the screen properl";
		runParser(term, null, term);
	}

	@Test
	public void itSearchesByWidthAndHeight() throws Exception {
		final String t1 = this.mockMediaMetadataStore.addFileWithInfoAndTags(new FileInfo(TimeUnit.SECONDS.toMillis(123), 1920, 1080), "tag");
		final String t2 = this.mockMediaMetadataStore.addFileWithInfoAndTags(new FileInfo(TimeUnit.SECONDS.toMillis(123), 2000, 2000), "tag");
		final String t3 = this.mockMediaMetadataStore.addFileWithInfoAndTags(new FileInfo(TimeUnit.SECONDS.toMillis(123), 3000, 3000), "tag");

		runQuery("t=tag w=1920 h=1080", t1);
		runQuery("t=tag w<2000", t1);

		runQuery("t=tag w>1920", t2, t3);
		runQuery("t=tag h>1080", t2, t3);

		runQuery("t=tag w>=2000 w<3000", t2);
	}

	@Test
	public void itDoesATopTagsSearch() throws Exception {
		mockMediaFileWithTags("desu", "foobar", "thing", "other");
		mockMediaFileWithTags("foobar", "desu");
		mockMediaFileWithTags("foobar", "thing");

		final List<TagFrequency> tagRes = DbSearchParser.parseSearchForTags("t=foobar", null).execute(this.mediaDb, 3, 0);
		assertThat(tagRes, contains(new TagFrequency("foobar", 3), new TagFrequency("desu", 2), new TagFrequency("thing", 2)));
	}

// Template tests.

	private static void runParser(final String input, final String expectedSql, final String... expectedTerms) {
		final DbSearch parsed = DbSearchParser.parseSearch(input, null, SortOrder.FILE.asc());
		if (expectedSql != null) assertEquals(expectedSql, parsed.getSql());
		assertThat(parsed.getTerms(), contains(expectedTerms));
	}

	private void runQuery(final String input, final String... expectedResults) throws SQLException {
		runQuery(input, null, expectedResults);
	}

	private void runQuery(final String input, final Set<BigInteger> authIds, final String... expectedResults) throws SQLException {
		final DbSearch parsed = DbSearchParser.parseSearch(input, authIds, SortOrder.FILE.asc());
		final List<String> results = parsed.execute(this.mediaDb);
		assertThat(results, containsInAnyOrder(expectedResults));
	}

// Builders.

	private String mockMediaTrackWithNameContaining (final String nameFragment) throws Exception {
		return this.mockMediaMetadataStore.addFileWithName(nameFragment);
	}

	private String mockMediaTrackWithNameContaining (final String nameFragment, final String nameSuffex) throws Exception {
		return this.mockMediaMetadataStore.addFileWithName(nameFragment, nameSuffex);
	}

	private String mockMediaFileWithTags (final String... tags) throws Exception {
		return this.mockMediaMetadataStore.addFileWithTags(tags);
	}

	private String mockMediaFileWithNameFragmentAndTags (final String nameFragment, final String... tags) throws Exception {
		return this.mockMediaMetadataStore.addFileWithNameAndTags(nameFragment, tags);
	}

}

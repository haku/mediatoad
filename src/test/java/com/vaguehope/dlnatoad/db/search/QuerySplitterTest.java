package com.vaguehope.dlnatoad.db.search;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class QuerySplitterTest {

	@Test
	public void itSplitsNull() throws Exception {
		assertEquals(Collections.emptyList(), QuerySplitter.split(null, 10));
	}

	@Test
	public void itSplitsWithMaxParts() throws Exception {
		testSplit("a b c d e f g h i j k l", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
	}

	@Test
	public void itSplits0() throws Exception {
		testSplit("f~some_awesome_band_desu", "f~some_awesome_band_desu");
	}

	@Test
	public void itSplits1() throws Exception {
		testSplit("t~some_awesome_band_desu", "t~some_awesome_band_desu");
	}

	@Test
	public void itSplits2() throws Exception {
		testSplit("t=foo f~some_folder", "t=foo", "f~some_folder");
	}

	@Test
	public void itSplits3() throws Exception {
		testSplit("t=bar f~some_folder", "t=bar", "f~some_folder");
	}

	@Test
	public void itSplits4() throws Exception {
		testSplit("(t=bar OR t=foo) f~some_folder", "(", "t=bar", "OR", "t=foo", ")", "f~some_folder");
	}

	@Test
	public void itSplits5() throws Exception {
		testSplit("f~some_folder (t=bar OR t=foo)", "f~some_folder", "(", "t=bar", "OR", "t=foo", ")");
	}

	@Test
	public void itSplits6() throws Exception {
		testSplit("f~some_folder AND (t=bar OR t=foo)", "f~some_folder", "AND", "(", "t=bar", "OR", "t=foo", ")");
	}

	@Test
	public void itSplits7() throws Exception {
		testSplit("f~some_folder AND AND (t=bar OR t=foo)", "f~some_folder", "AND", "AND", "(", "t=bar", "OR", "t=foo", ")");
	}

	@Test
	public void itSplits8() throws Exception {
		testSplit("f~awesome'\"*%_\\)(band", "f~awesome'\"*%_\\)(band");
	}

	@Test
	public void itSplits9() throws Exception {
		testSplit("t=some_awesome_band_desu t=happy_track_nyan~", "t=some_awesome_band_desu", "t=happy_track_nyan~");
	}

	@Test
	public void itSplits10() throws Exception {
		testSplit("t=some_awesome_band_desu t=happy_track_nyan~", "t=some_awesome_band_desu", "t=happy_track_nyan~");
	}

	@Test
	public void itSplits11() throws Exception {
		testSplit("some_awesome_band_desu happy_track_nyan~", "some_awesome_band_desu", "happy_track_nyan~");
	}

	@Test
	public void itSplits12() throws Exception {
		testSplit("( OR some_media_file_2807749155109", "(", "OR", "some_media_file_2807749155109");
	}

	@Test
	public void itSplits13() throws Exception {
		testSplit("some_media_tag_2807754086566", "some_media_tag_2807754086566");
	}

	@Test
	public void itSplits14() throws Exception {
		testSplit("T=pl_desu", "T=pl_desu");
	}

	@Test
	public void itSplits15() throws Exception {
		testSplit("some_media_file_2807764147898 OR )", "some_media_file_2807764147898", "OR", ")");
	}

	@Test
	public void itSplits16() throws Exception {
		testSplit("f~\"some 'awesome\\\" band' desu\"", "f~\"some 'awesome\\\" band' desu\"");
	}

	@Test
	public void itSplits17() throws Exception {
		testSplit("t=abc -t=foobar", "t=abc", "-t=foobar");
	}

	@Test
	public void itSplits18() throws Exception {
		testSplit("T~some_awesome_band_desu", "T~some_awesome_band_desu");
	}

	@Test
	public void itSplits19() throws Exception {
		testSplit("f~.myext$", "f~.myext$");
	}

	@Test
	public void itSplits20() throws Exception {
		testSplit("f~'some \"awesome\\' band\" desu'", "f~'some \"awesome\\' band\" desu'");
	}

	@Test
	public void itSplits21() throws Exception {
		testSplit("t~some_awesome_band_desu$", "t~some_awesome_band_desu$");
	}

	@Test
	public void itSplits22() throws Exception {
		testSplit("some_media_file_2807797423773)", "some_media_file_2807797423773", ")");
	}

	@Test
	public void itSplits23() throws Exception {
		testSplit("  some_awesome_band_desu OR some_other_thing ", "some_awesome_band_desu", "OR", "some_other_thing");
	}

	@Test
	public void itSplits24() throws Exception {
		testSplit("  some_awesome_band_desu OR OR some_other_thing ", "some_awesome_band_desu", "OR", "OR", "some_other_thing");
	}

	@Test
	public void itSplits25() throws Exception {
		testSplit("F~some_awesome_band_desu", "F~some_awesome_band_desu");
	}

	@Test
	public void itSplits26() throws Exception {
		testSplit("t=some\" \\'media\\\" tag \"", "t=some\" \\'media\\\" tag \"");
	}

	@Test
	public void itSplits27() throws Exception {
		testSplit("OR some_media_file_2807813427359", "OR", "some_media_file_2807813427359");
	}

	@Test
	public void itSplits28() throws Exception {
		testSplit("t=pl_desu", "t=pl_desu");
	}

	@Test
	public void itSplits29() throws Exception {
		testSplit("(some_media_file_2807824414355", "(", "some_media_file_2807824414355");
	}

	@Test
	public void itSplits30() throws Exception {
		testSplit("t=some' \\\"media\\' tag '", "t=some' \\\"media\\' tag '");
	}

	@Test
	public void itSplits31() throws Exception {
		testSplit("some_awesome_band_desu", "some_awesome_band_desu");
	}

	@Test
	public void itSplits32() throws Exception {
		testSplit("t=abc -t~foo", "t=abc", "-t~foo");
	}

	@Test
	public void itSplits33() throws Exception {
		testSplit("some_media_file_2807842361757", "some_media_file_2807842361757");
	}

	@Test
	public void itSplits34() throws Exception {
		testSplit("t~^some_awesome_band_desu", "t~^some_awesome_band_desu");
	}

	@Test
	public void itSplits35() throws Exception {
		testSplit("f~'some awesome\" band desu'", "f~'some awesome\" band desu'");
	}

	@Test
	public void itSplits36() throws Exception {
		testSplit("f~\"some awesome' band desu\"", "f~\"some awesome' band desu\"");
	}

	@Test
	public void itSplits37() throws Exception {
		testSplit("foobar desu", "foobar", "desu");
	}

	@Test
	public void itSplits38() throws Exception {
		testSplit("foobar	desu", "foobar", "desu");
	}

	@Test
	public void itSplits39() throws Exception {
		testSplit("foobar　desu", "foobar", "desu");
	}

	@Test
	public void itSplits40() throws Exception {
		testSplit("t=some' media\\' tag '", "t=some' media\\' tag '");
	}

	@Test
	public void itSplits41() throws Exception {
		testSplit("'some awesome\" band desu'", "'some awesome\" band desu'");
	}

	@Test
	public void itSplits42() throws Exception {
		testSplit("\"some awesome' band desu\"", "\"some awesome' band desu\"");
	}

	@Test
	public void itSplits43() throws Exception {
		testSplit("t=some\" media\\\" tag \"", "t=some\" media\\\" tag \"");
	}

	@Test
	public void itSplits44() throws Exception {
		testSplit("f~foobar t=desu", "f~foobar", "t=desu");
	}

	@Test
	public void itSplits45() throws Exception {
		testSplit("t=awesome'\"*%_\\)(band", "t=awesome'\"*%_\\)(band");
		testSplit("t=awesome'\"*%_\\)(", "t=awesome'\"*%_\\)(");
	}

	@Test
	public void itSplits46() throws Exception {
		testSplit("some_media_file_2807882622302 OR", "some_media_file_2807882622302", "OR");
	}

	@Test
	public void itSplits47() throws Exception {
		testSplit("t=abc -f~foo", "t=abc", "-f~foo");
	}

	@Test
	public void itSplits48() throws Exception {
		testSplit("(abc)", "(", "abc", ")");
		testSplit(" (abc) ", "(", "abc", ")");
		testSplit("x(abc)x", "x(abc)x");
	}

	@Test
	public void itSplits49() throws Exception {
		testSplit("t=\"a very long tag that does not fit on the screen properl", "t=\"a very long tag that does not fit on the screen properl");
	}

	private static void testSplit(final String input, final String... expected) {
		assertEquals(Arrays.asList(expected), QuerySplitter.split(input, 10));
	}

}

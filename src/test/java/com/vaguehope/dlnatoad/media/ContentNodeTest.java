package com.vaguehope.dlnatoad.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Ignore;
import org.junit.Test;

public class ContentNodeTest {

	@Test
	public void itDoesNotModifyTitleIfNoAuthList() throws Exception {
		final ContentNode n = new ContentNode("id", ContentGroup.AUDIO.getId(), "title", new File(""), null, null, null);
		assertEquals("title", n.getTitle());
	}

	@Test
	public void itSortsNodesCaseSensitive() throws Exception {
		final ContentNode n = new ContentNode("id", ContentGroup.AUDIO.getId(), "title", new File(""), null, null, null);

		n.addNodeIfAbsent(new ContentNode("1", n.getId(), "1", "2"));
		n.addNodeIfAbsent(new ContentNode("2", n.getId(), "2", "B"));
		n.addNodeIfAbsent(new ContentNode("3", n.getId(), "3", "1"));
		n.addNodeIfAbsent(new ContentNode("4", n.getId(), "4", "c"));
		n.addNodeIfAbsent(new ContentNode("5", n.getId(), "5", "a"));

		final List<String> actual = n.getCopyOfNodes().stream().map(i -> i.getSortKey()).collect(Collectors.toList());
		assertThat(actual, contains("1", "2", "B", "a", "c"));
	}

	@Test
	public void itSortsItemsCaseInsitive() throws Exception {
		final ContentNode n = new ContentNode("id", ContentGroup.AUDIO.getId(), "title", new File(""), null, null, null);

		n.addItemIfAbsent(new ContentItem("1", n.getId(), "2", null, null));
		n.addItemIfAbsent(new ContentItem("2", n.getId(), "B", null, null));
		n.addItemIfAbsent(new ContentItem("3", n.getId(), "1", null, null));
		n.addItemIfAbsent(new ContentItem("4", n.getId(), "c", null, null));
		n.addItemIfAbsent(new ContentItem("5", n.getId(), "a", null, null));

		final List<String> actual = n.getCopyOfItems().stream().map(i -> i.getTitle()).collect(Collectors.toList());
		assertThat(actual, contains("1", "2", "a", "B", "c"));
	}

	@Ignore("Micro benchmark for checking performance of sort on insert.")
	@Test
	public void itAddsManyRandomItems() throws Exception {
		final ContentNode n = new ContentNode("id", ContentGroup.AUDIO.getId(), "title", new File(""), null, null, null);
		final int count = 5000;
		final List<String> titles = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			titles.add(RandomStringUtils.randomPrint(10, 50));
		}
		Collections.shuffle(titles);
		final Iterator<String> titleIttr = titles.iterator();

		final long start = System.nanoTime();
		for (int i = 0; i < count; i++) {
			n.addItemIfAbsent(new ContentItem(String.valueOf(i), n.getId(), titleIttr.next(), null, null));
		}
		final long end = System.nanoTime();
		System.out.println("Adding and sorting took: " + TimeUnit.NANOSECONDS.toMillis(end - start) + "ms");
	}

}

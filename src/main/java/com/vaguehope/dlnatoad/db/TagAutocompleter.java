package com.vaguehope.dlnatoad.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/*
 * Binary search and then step backwards/forwards inspired by:
 * https://stackoverflow.com/questions/9543046/implement-binary-search-with-string-prefix
 */
public class TagAutocompleter {

	private static final int MAX_SUGGESTIONS = 20; // TODO Remove from AutocompleteServlet?
	private static final Logger LOG = LoggerFactory.getLogger(TagAutocompleter.class);

	private final MediaDb db;
	private FragmentAndTag[] tagsArr;
	private FragmentAndTag[] fragmentsArr;

	public TagAutocompleter(final MediaDb db) {
		this.db = db;
	}

	public List<TagFrequency> suggestTags(final String input) {
		return binarySearch(this.tagsArr, input);
	}

	public List<TagFrequency> suggestFragments(final String input) {
		return binarySearch(this.fragmentsArr, input);
	}

	private static List<TagFrequency> binarySearch(final FragmentAndTag[] idx, final String input) {
		final FragmentPrefixComp comp = new FragmentPrefixComp(input);
		final int randomIndex = Arrays.binarySearch(idx, new FragmentAndTag(input, "unused", -1), comp);
		if (randomIndex < 0) return Collections.emptyList();

		int rangeStarts = randomIndex;
		int rangeEnds = randomIndex;
		while (rangeStarts > -1
				&& idx[rangeStarts].fragment.toLowerCase().startsWith(input.toLowerCase())) {
			rangeStarts--;
		}
		while (rangeEnds < idx.length
				&& idx[rangeEnds].fragment.toLowerCase().startsWith(input.toLowerCase())) {
			rangeEnds++;
		}
		final FragmentAndTag[] matches = Arrays.copyOfRange(idx, rangeStarts + 1, rangeEnds);
		Arrays.sort(matches, FragmentAndTag.Order.COUNT_DESC);

		final List<TagFrequency> ret = new ArrayList<>();
		for (int i = 0; i < matches.length && i < MAX_SUGGESTIONS; i++) {
			final FragmentAndTag f = matches[i];
			ret.add(new TagFrequency(f.tag, f.fileCount));
		}
		return ret;
	}

	private static class FragmentPrefixComp implements Comparator<FragmentAndTag> {
		private final String prefix;

		public FragmentPrefixComp(final String prefix) {
			this.prefix = prefix;
		}

		@Override
		public int compare(final FragmentAndTag o1, final FragmentAndTag o2) {
			final String p1;
			if (o1.fragment.length() < this.prefix.length()) {
				p1 = o1.fragment;
			}
			else {
				p1 = o1.fragment.substring(0, this.prefix.length());
			}
			return p1.compareToIgnoreCase(o2.fragment);
		}
	}

	public void generateIndex() throws SQLException {
		final List<TagFrequency> tags = this.db.getAllTagsNotMissingNotDeleted();
		generateTagsIndex(tags);
		generateFragmentsIndex(tags);
	}

	private void generateTagsIndex(final List<TagFrequency> tags) {
		final List<FragmentAndTag> idx = new ArrayList<>(tags.size());
		for (final TagFrequency tf : tags) {
			idx.add(new FragmentAndTag(tf.getTag(), tf.getTag(), tf.getCount()));
		}
		LOG.info("Tags index: {}", idx.size());
		this.tagsArr = idx.toArray(new FragmentAndTag[idx.size()]);
	}

	private void generateFragmentsIndex(final List<TagFrequency> tags) {
		final List<FragmentAndTag> fragments = makeFragments(tags);
		LOG.info("Fragments index: {}", fragments.size());

		fragments.sort(FragmentAndTag.Order.FRAGMENT_ASC);
		final Multiset<String> fragmentCounts = HashMultiset.create();
		for (final Iterator<FragmentAndTag> i = fragments.iterator(); i.hasNext();) {
			final FragmentAndTag n = i.next();
			if (fragmentCounts.count(n.fragment) < MAX_SUGGESTIONS) {
				fragmentCounts.add(n.fragment);
			}
			else {
				i.remove();
			}
		}

		LOG.info("Top fragments: {}", fragments.size());
		this.fragmentsArr = fragments.toArray(new FragmentAndTag[fragments.size()]);
	}

	private static List<FragmentAndTag> makeFragments(final List<TagFrequency> allTags) {
		final List<FragmentAndTag> allFragments = new ArrayList<>();
		for (final TagFrequency tag : allTags) {
			allFragments.addAll(makeFragments(tag.getTag(), tag.getCount()));
		}
		return allFragments;
	}

	static List<FragmentAndTag> makeFragments(final String tag, final int fileCount) {
		if (tag.length() < 2) return Collections.emptyList();
		final List<FragmentAndTag> ret = new ArrayList<>(); // TODO would simple array be faster?
		for (int i = 1; i < tag.length(); i++) {
			final String frag = tag.substring(i);
			if (Character.isWhitespace(frag.charAt(0))) continue;
			ret.add(new FragmentAndTag(frag, tag, fileCount));
		}
		return ret;
	}

	static class FragmentAndTag {
		final String fragment;
		final String tag;
		final int fileCount;

		public FragmentAndTag(final String fragment, final String tag, final int fileCount) {
			if (fragment == null) throw new IllegalArgumentException("fragment is null.");
			if (tag == null) throw new IllegalArgumentException("tag is null.");
			this.fragment = fragment;
			this.tag = tag;
			this.fileCount = fileCount;
		}

		@Override
		public String toString() {
			return String.format("FragmentAndTag{%s, %s, %s}", this.fragment, this.tag, this.fileCount);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.fragment, this.tag, this.fileCount);
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == null) return false;
			if (this == obj) return true;
			if (!(obj instanceof FragmentAndTag)) return false;
			final FragmentAndTag that = (FragmentAndTag) obj;
			return Objects.equals(this.fragment, that.fragment)
					&& Objects.equals(this.tag, that.tag)
					&& Objects.equals(this.fileCount, that.fileCount);
		}

		public enum Order implements Comparator<FragmentAndTag> {
			FRAGMENT_ASC {
				@Override
				public int compare(final FragmentAndTag a, final FragmentAndTag b) {
					final int c = a.fragment.compareTo(b.fragment);
					if (c != 0) return c;
					return COUNT_DESC.compare(a, b);
				}
			},
			COUNT_DESC {
				@Override
				public int compare(final FragmentAndTag a, final FragmentAndTag b) {
					final int c = Long.compare(b.fileCount, a.fileCount);
					if (c != 0) return c;
					return TAG_ASC.compare(a, b);
				}
			},
			TAG_ASC {
				@Override
				public int compare(final FragmentAndTag a, final FragmentAndTag b) {
					return a.tag.compareTo(b.tag);
				}
			}
		}
	}

}

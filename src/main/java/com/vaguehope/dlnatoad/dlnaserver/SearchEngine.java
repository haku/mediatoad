package com.vaguehope.dlnatoad.dlnaserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.teleal.cling.support.contentdirectory.ContentDirectoryException;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

public class SearchEngine {

	private static final Pattern CONTAINS = Pattern.compile("(.+)\\s+contains\\s+\"(.+)\"");

	public SearchEngine () {}

	public List<Item> search (final ContentNode contentNode, final String searchCriteria) throws ContentDirectoryException {
		// FIXME Force parse a title query ignoring everything else.
		String term = null;
		for (final String part : searchCriteria.replaceAll("[\\(\\)]", "").split("(?i)\\band\\b|\\bor\\b")) {
			final Matcher m = CONTAINS.matcher(part.trim());
			if (m.matches() && "dc:title".equalsIgnoreCase(m.group(1))) {
				term = m.group(2);
				break;
			}
		}
		if (term == null || term.length() < 1) throw new ContentDirectoryException(ContentDirectoryErrorCodes.UNSUPPORTED_SEARCH_CRITERIA, "Do not know how to parse: " + searchCriteria);
		return filterByTitleSubstring(contentNode.getContainer(), term.toLowerCase());
	}

	/**
	 * Lazy recursive impl.
	 */
	private static List<Item> filterByTitleSubstring (final Container container, final String lcaseTerm) {
		final List<Item> results = new ArrayList<>();
		for (final Item ci : container.getItems()) {
			if (ci.getTitle().toLowerCase(Locale.ENGLISH).contains(lcaseTerm)) results.add(ci);
		}
		if (container.getContainers() != null) {
			for (final Container childContainer : container.getContainers()) {
				results.addAll(filterByTitleSubstring(childContainer, lcaseTerm));
			}
		}
		return results;
	}

}

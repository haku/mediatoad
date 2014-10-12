package com.vaguehope.dlnatoad.dlnaserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.model.types.ErrorCode;
import org.teleal.cling.support.contentdirectory.AbstractContentDirectoryService;
import org.teleal.cling.support.contentdirectory.ContentDirectoryErrorCode;
import org.teleal.cling.support.contentdirectory.ContentDirectoryException;
import org.teleal.cling.support.contentdirectory.DIDLParser;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.BrowseResult;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.SortCriterion;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentDirectoryService extends AbstractContentDirectoryService {

	// error codes from UPnP-av-ContentDirectory-v1-Service
	private static final int NO_SUCH_OBJECT = 701; // The specified ObjectID is invalid.
	private static final int UNSUPPORTED_SEARCH_CRITERIA = 708; // The search criteria specified is not supported or is invalid.
	private static final int UNSUPPORTED_SORT_CRITERIA = 709; // The sort criteria specified is not supported or is invalid.
	private static final int UNSUPPORTED_SEARCH_CONTAINER = 710; // The specified ContainerID is invalid or identifies an object that is not a container.
	private static final int RESTRICTED_OBJECT = 711; // Operation failed because the restricted attribute of object is set to true.
	private static final int BAD_METADATA = 712; // Operation fails because it would result in invalid or disallowed metadata in current object.
	private static final int RESTRICTED_PARENT_OBJECT = 713; // Operation failed because the restricted attribute of parent object is set to true.
	private static final int CANNOT_PROCESS = 720; // Cannot process the request.

	private static final Logger LOG = LoggerFactory.getLogger(ContentDirectoryService.class);

	private final ContentTree contentTree;

	public ContentDirectoryService (final ContentTree contentTree) {
		super(
				Arrays.asList("dc:title", "upnp:class"), // also "dc:creator", "dc:date", "res@size"
				Arrays.asList("dc:title")); // also "dc:creator", "dc:date", "res@size"
		this.contentTree = contentTree;
	}

	/**
	 * Root is requested with objectID="0".
	 */
	@Override
	public BrowseResult browse (final String objectID, final BrowseFlag browseFlag, final String filter, final long firstResult, final long maxResults, final SortCriterion[] orderby) throws ContentDirectoryException {
		LOG.info("browse: {} ({}, {})", objectID, firstResult, maxResults);
		try {
			final ContentNode contentNode = this.contentTree.getNode(objectID);
			if (contentNode == null) return new BrowseResult("", 0, 0);

			if (contentNode.isItem()) {
				final DIDLContent didl = new DIDLContent();
				didl.addItem(contentNode.getItem());
				return new BrowseResult(new DIDLParser().generate(didl), 1, 1);
			}

			final Container contentContainer = contentNode.getContainer();

			if (browseFlag == BrowseFlag.METADATA) {
				final DIDLContent didl = new DIDLContent();
				didl.addContainer(contentContainer);
				return new BrowseResult(new DIDLParser().generate(didl), 1, 1);
			}

			return toRangedResult(contentContainer.getContainers(), contentContainer.getItems(), firstResult, maxResults);
		}
		catch (final Exception e) {
			LOG.warn(String.format("Failed to generate directory listing" +
					" (objectID=%s, browseFlag=%s, filter=%s, firstResult=%s, maxResults=%s, orderby=%s).",
					objectID, browseFlag, filter, firstResult, maxResults, Arrays.toString(orderby)), e);
			throw new ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, e.toString()); // NOSONAR
		}
	}

	private static final Pattern CONTAINS = Pattern.compile("(.+)\\s+contains\\s+\"(.+)\"");

	@Override
	public BrowseResult search (final String containerId, final String searchCriteria,
			final String filter, final long firstResult, final long maxResults,
			final SortCriterion[] orderBy) throws ContentDirectoryException {
		LOG.info("search: {}, {}, {} ({}, {}, {})", containerId, searchCriteria, filter, firstResult, maxResults, Arrays.toString(orderBy));
		try {
			final ContentNode contentNode = this.contentTree.getNode(containerId);
			if (contentNode == null) return new BrowseResult("", 0, 0);
			if (contentNode.isItem()) throw new ContentDirectoryException(UNSUPPORTED_SEARCH_CONTAINER, "Can not seach inside in an item.");

			// FIXME Force parse a title query ignoring everything else.
			String term = null;
			for (final String part : searchCriteria.replaceAll("[\\(\\)]", "").split("(?i)\\band\\b|\\bor\\b")) {
				final Matcher m = CONTAINS.matcher(part.trim());
				if (m.matches() && "dc:title".equalsIgnoreCase(m.group(1))) {
					term = m.group(2);
					break;
				}
			}
			if (term == null || term.length() < 1) throw new ContentDirectoryException(UNSUPPORTED_SEARCH_CRITERIA, "Do not know how to parse: " + searchCriteria);
			final List<Item> results = filterByTitleSubstring(contentNode.getContainer(), term.toLowerCase());

			return toRangedResult(Collections.<Container> emptyList(), results, firstResult, maxResults);
		}
		catch (final ContentDirectoryException e) {
			LOG.warn(String.format("Failed to parse search request" +
					" (containerId=%s, searchCriteria=%s, filter=%s, firstResult=%s, maxResults=%s, orderby=%s).",
					containerId, searchCriteria, filter, firstResult, maxResults, Arrays.toString(orderBy)));
			throw e;
		}
		catch (final Exception e) {
			LOG.warn(String.format("Failed to generate search results" +
					" (containerId=%s, searchCriteria=%s, filter=%s, firstResult=%s, maxResults=%s, orderby=%s).",
					containerId, searchCriteria, filter, firstResult, maxResults, Arrays.toString(orderBy)), e);
			throw new ContentDirectoryException(ErrorCode.ACTION_FAILED, e.toString());
		}
	}

	private static BrowseResult toRangedResult (final List<Container> containers, final List<Item> items, final long firstResult, final long maxResults) throws Exception {
		final DIDLContent didl = new DIDLContent();
		if (containers.size() > firstResult) {
			final int from = (int) firstResult;
			final int to = Math.min((int) (firstResult + maxResults), containers.size());
			didl.setContainers(containers.subList(from, to));
		}
		if (didl.getContainers().size() < maxResults) {
			final int from = (int) Math.max(firstResult - containers.size(), 0);
			final int to = Math.min(items.size(), from + (int) (maxResults - didl.getContainers().size()));
			didl.setItems(items.subList(from, to));
		}
		return new BrowseResult(new DIDLParser().generate(didl),
				didl.getContainers().size() + didl.getItems().size(),
				containers.size() + items.size());
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

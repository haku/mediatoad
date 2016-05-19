package com.vaguehope.dlnatoad.dlnaserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.model.types.ErrorCode;
import org.fourthline.cling.support.contentdirectory.AbstractContentDirectoryService;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.SortCriterion;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentDirectoryService extends AbstractContentDirectoryService {

	private static final Logger LOG = LoggerFactory.getLogger(ContentDirectoryService.class);

	private final ContentTree contentTree;
	private final SearchEngine searchEngine;

	public ContentDirectoryService (final ContentTree contentTree, final SearchEngine queryEngine) {
		super(
				Arrays.asList("dc:title", "upnp:class"), // also "dc:creator", "dc:date", "res@size"
				Arrays.asList("dc:title")); // also "dc:creator", "dc:date", "res@size"
		this.contentTree = contentTree;
		this.searchEngine = queryEngine;
	}

	/**
	 * Root is requested with objectID="0".
	 */
	@Override
	public BrowseResult browse (final String objectID, final BrowseFlag browseFlag, final String filter, final long firstResult, final long maxResults, final SortCriterion[] orderby) throws ContentDirectoryException {
		final long startTime = System.nanoTime();
		try {
			final ContentNode contentNode = this.contentTree.getNode(objectID);
			if (contentNode == null) return new BrowseResult(new DIDLParser().generate(new DIDLContent()), 0, 0);

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

			// toRangedResult() uses List.sublist(),
			// so make local copies.
			final List<Container> containers;
			final List<Item> items;
			synchronized (contentContainer) {
				containers = new ArrayList<Container>(contentContainer.getContainers());
				items = new ArrayList<Item>(contentContainer.getItems());
			}
			return toRangedResult(containers, items, firstResult, maxResults);
		}
		catch (final Exception e) {
			LOG.warn(String.format("Failed to generate directory listing" +
					" (objectID=%s, browseFlag=%s, filter=%s, firstResult=%s, maxResults=%s, orderby=%s).",
					objectID, browseFlag, filter, firstResult, maxResults, Arrays.toString(orderby)), e);
			throw new ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, e.toString()); // NOSONAR
		}
		finally {
			LOG.info("browse: {} ({}, {}) in {}ms.",
					objectID, firstResult, maxResults,
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
		}
	}

	@Override
	public BrowseResult search (final String containerId, final String searchCriteria,
			final String filter, final long firstResult, final long maxResults,
			final SortCriterion[] orderBy) throws ContentDirectoryException {
		final long startTime = System.nanoTime();
		try {
			final ContentNode contentNode = this.contentTree.getNode(containerId);
			if (contentNode == null) return new BrowseResult("", 0, 0);
			if (contentNode.isItem()) throw new ContentDirectoryException(ContentDirectoryErrorCodes.UNSUPPORTED_SEARCH_CONTAINER, "Can not seach inside in an item.");
			// TODO cache search results to make pagination faster.
			return toRangedResult(Collections.<Container> emptyList(), this.searchEngine.search(contentNode, searchCriteria), firstResult, maxResults);
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
		finally {
			LOG.info("search: {}, {}, {} ({}, {}, {}) in {}ms.",
					containerId, searchCriteria, filter, firstResult, maxResults, Arrays.toString(orderBy),
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
		}
	}

	private static BrowseResult toRangedResult (final List<Container> containers, final List<Item> items, final long firstResult, final long maxResultsParam) throws Exception {
		final long maxResults = maxResultsParam == 0 ? containers.size() + items.size() : maxResultsParam;

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

}

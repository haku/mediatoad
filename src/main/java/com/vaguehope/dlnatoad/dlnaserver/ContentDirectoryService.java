package com.vaguehope.dlnatoad.dlnaserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.support.contentdirectory.AbstractContentDirectoryService;
import org.teleal.cling.support.contentdirectory.ContentDirectoryErrorCode;
import org.teleal.cling.support.contentdirectory.ContentDirectoryException;
import org.teleal.cling.support.contentdirectory.DIDLParser;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.BrowseResult;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.SortCriterion;
import org.teleal.cling.support.model.container.Container;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentDirectoryService extends AbstractContentDirectoryService {

	private static final Logger LOG = LoggerFactory.getLogger(ContentDirectoryService.class);

	private final ContentTree contentTree;

	public ContentDirectoryService (final ContentTree contentTree) {
		this.contentTree = contentTree;
	}

	@Override
	public BrowseResult browse (final String objectID, final BrowseFlag browseFlag, final String filter, final long firstResult, final long maxResults, final SortCriterion[] orderby) throws ContentDirectoryException {
		LOG.info("browse: {} ({}, {})", objectID, firstResult, maxResults);
		try {
			final DIDLContent didl = new DIDLContent();
			final ContentNode contentNode = this.contentTree.getNode(objectID);

			if (contentNode == null) return new BrowseResult("", 0, 0);

			if (contentNode.isItem()) {
				didl.addItem(contentNode.getItem());
				return new BrowseResult(new DIDLParser().generate(didl), 1, 1);
			}

			final Container contentContainer = contentNode.getContainer();

			if (browseFlag == BrowseFlag.METADATA) {
				didl.addContainer(contentContainer);
				return new BrowseResult(new DIDLParser().generate(didl), 1, 1);
			}

			if (contentContainer.getContainers().size() > firstResult) {
				final int from = (int) firstResult;
				final int to = Math.min((int) (firstResult + maxResults), contentContainer.getContainers().size());
				didl.setContainers(contentContainer.getContainers().subList(from, to));
			}
			if (didl.getContainers().size() < maxResults) {
				final int from = (int) Math.max(firstResult - contentContainer.getContainers().size(), 0);
				final int to = Math.min(contentContainer.getItems().size(), from + (int) (maxResults - didl.getContainers().size()));
				didl.setItems(contentContainer.getItems().subList(from, to));
			}
			return new BrowseResult(new DIDLParser().generate(didl),
					didl.getContainers().size() + didl.getItems().size(),
					contentContainer.getChildCount().intValue());
		}
		catch (final Exception e) {
			LOG.warn("Failed to generate directory listing.", e);
			throw new ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, e.toString()); // NOSONAR
		}
	}

	@Override
	public BrowseResult search (final String containerId, final String searchCriteria,
			final String filter, final long firstResult, final long maxResults,
			final SortCriterion[] orderBy) throws ContentDirectoryException {
		// You can override this method to implement searching!
		return super.search(containerId, searchCriteria, filter, firstResult, maxResults, orderBy);
	}
}

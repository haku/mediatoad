package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.ServiceType;
import org.fourthline.cling.support.contentdirectory.callback.Search;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.SortCriterion;
import org.fourthline.cling.support.model.item.Item;

import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;
import com.vaguehope.dlnatoad.dlnaserver.SearchEngine;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.util.FileHelper;

public class SearchServlet extends HttpServlet {

	static final String PARAM_QUERY = "query";
	static final String PARAM_REMOTE = "remote";

	private static final int MAX_RESULTS = 500;
	private static final String ROOT_CONTENT_ID = "0"; // Root id of '0' is in the spec.
	private static final ServiceType CONTENT_DIRECTORY_TYPE = ServiceType.valueOf("urn:schemas-upnp-org:service:ContentDirectory:1");

	private static final long serialVersionUID = -3882119061427383748L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final MediaDb mediaDb;
	private final UpnpService upnpService;
	private final SearchEngine searchEngine;

	public SearchServlet(final ServletCommon servletCommon, final ContentTree contentTree, final MediaDb mediaDb, final UpnpService upnpService) {
		this(servletCommon, contentTree, mediaDb, upnpService, new SearchEngine());
	}

	protected SearchServlet(final ServletCommon servletCommon, final ContentTree contentTree, final MediaDb mediaDb, final UpnpService upnpService, final SearchEngine searchEngine) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
		this.upnpService = upnpService;
		this.searchEngine = searchEngine;
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String query = StringUtils.trimToEmpty(req.getParameter(PARAM_QUERY));

		ServletCommon.setHtmlContentType(resp);
		@SuppressWarnings("resource")
		final PrintWriter w = resp.getWriter();
		this.servletCommon.headerAndStartBody(w, StringUtils.defaultString(query, "Search"));
		this.servletCommon.printLinkRow(req, w);

		if (!StringUtils.isBlank(query)) {
			final String username = ReqAttr.USERNAME.get(req);
			final String upnpQuery = String.format("(dc:title contains \"%s\")", query);

			try {
				final List<ContentItem> results;
				if (this.mediaDb != null) {
					final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);
					final List<String> ids = DbSearchParser.parseSearch(query, authIds).execute(this.mediaDb, MAX_RESULTS, 0);
					results = this.contentTree.getItemsForIds(ids, username);
				}
				else {
					final ContentNode rootNode = this.contentTree.getNode(ContentGroup.ROOT.getId());
					results = this.searchEngine.search(rootNode, upnpQuery, MAX_RESULTS, username);
				}
				this.servletCommon.printItemsAndImages(w, results);

				// Only do remote search if local does not error.
				final String remote = StringUtils.trimToEmpty(req.getParameter(PARAM_REMOTE));
				if (ReqAttr.ALLOW_REMOTE_SEARCH.get(req) && StringUtils.isNotBlank(remote)) {
					remoteSearch(upnpQuery, w);
				}
			}
			catch (final Exception e) {
				w.print("<pre>Failed to run query: ");
				e.printStackTrace(w);
				w.println("</pre>");
			}
		}

		this.servletCommon.endBody(w);
	}

	private void remoteSearch(final String searchCriteria, final PrintWriter w) throws InterruptedException, ExecutionException {
		final Collection<RemoteService> contentDirectoryServices = findAllContentDirectoryServices();
		searchContentDirectories(contentDirectoryServices, searchCriteria, w);
	}

	private Collection<RemoteService> findAllContentDirectoryServices() {
		final Collection<RemoteService> ret = new ArrayList<>();
		for (final RemoteDevice rd : this.upnpService.getRegistry().getRemoteDevices()) {
			ret.addAll(Arrays.asList(rd.findServices(CONTENT_DIRECTORY_TYPE)));
		}
		return ret;
	}

	private void searchContentDirectories(final Collection<RemoteService> contentDirectoryServices, final String searchCriteria, final PrintWriter w) throws InterruptedException, ExecutionException {
		final Collection<CDSearch> searches = new ArrayList<>();
		final Collection<Future<?>> futures = new ArrayList<>();
		for (final RemoteService cd : contentDirectoryServices) {
			final CDSearch cds = new CDSearch(cd, ROOT_CONTENT_ID, searchCriteria, Search.CAPS_WILDCARD, 0, (long) MAX_RESULTS);
			futures.add(this.upnpService.getControlPoint().execute(cds));
			searches.add(cds);
		}

		for (final Future<?> f : futures) {
			f.get();
		}

		for (final CDSearch s : searches) {
			final String title = s.getService().getDevice().getDetails().getFriendlyName();

			final String err = s.getErr();
			if (StringUtils.isNotBlank(err)) {
				w.println("<h3>" + title + "</h3>");
				w.println(err);
				continue;
			}

			final List<Item> items = s.getPayload().getItems();
			w.println("<h3>" + title + " items: " + items.size() + "</h3>");
			w.println("<ul>");
			for (final Item item : items) {
				printRemoteItem(item, w);
			}
			w.println("</ul>");
		}
	}

	private static void printRemoteItem(final Item item, final PrintWriter w) {
		w.print("<li>");
		w.print(item.getTitle());
		for (final Res r : item.getResources()) {
			w.print(" <a href=\"" + r.getValue() + "\">");
			w.print(r.getProtocolInfo().getContentFormat());
			if (r.getSize() != null) {
				w.print(" (" + FileHelper.readableFileSize(r.getSize()) + ")");
			}
			w.print("</a>");
		}
		w.println("</li>");
	}

	private static class CDSearch extends Search {

		private final Service<?, ?> service;
		private final AtomicReference<DIDLContent> payload = new AtomicReference<>();
		private final AtomicReference<String> err = new AtomicReference<>();

		public CDSearch(final Service<?, ?> service, final String containerId, final String searchCriteria, final String filter, final long firstResult, final Long maxResults, final SortCriterion... orderBy) {
			super(service, containerId, searchCriteria, filter, firstResult, maxResults, orderBy);
			this.service = service;
		}

		public Service<?, ?> getService() {
			return this.service;
		}

		public DIDLContent getPayload() {
			return this.payload.get();
		}

		public String getErr() {
			return this.err.get();
		}

		@Override
		public void failure(final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
			this.err.set("Failed to search content directory: " + defaultMsg);
		}

		@Override
		public void received(final ActionInvocation invocation, final DIDLContent didl) {
			this.payload.set(didl);
		}

		@Override
		public void updateStatus(final Status status) {
			// Unused.
		}
	}

}

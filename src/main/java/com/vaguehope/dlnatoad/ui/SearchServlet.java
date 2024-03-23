package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jupnp.UpnpService;
import org.jupnp.model.ModelUtil;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.ServiceType;
import org.jupnp.support.contentdirectory.callback.Search;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.item.Item;

import com.github.mustachejava.Mustache;
import com.google.common.net.UrlEscapers;
import com.google.common.util.concurrent.ListenableFuture;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;
import com.vaguehope.dlnatoad.dlnaserver.SearchEngine;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.rpc.MediaGrpc.MediaFutureStub;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.MediaItem;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchReply;
import com.vaguehope.dlnatoad.rpc.MediaToadProto.SearchRequest;
import com.vaguehope.dlnatoad.rpc.client.RemoteInstance;
import com.vaguehope.dlnatoad.rpc.client.RpcClient;
import com.vaguehope.dlnatoad.ui.templates.PageScope;
import com.vaguehope.dlnatoad.ui.templates.ResultGroupScope;
import com.vaguehope.dlnatoad.ui.templates.SearchResultsScope;
import com.vaguehope.dlnatoad.util.FileHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;

public class SearchServlet extends HttpServlet {

	static final String PARAM_QUERY = "query";
	static final String PARAM_PAGE_LIMIT = "limit";
	static final String PARAM_PAGE_OFFSET = "offset";
	static final String PARAM_REMOTE = "remote";

	static final int MAX_RESULTS = 500;
	private static final String ROOT_CONTENT_ID = "0"; // Root id of '0' is in the spec.
	private static final ServiceType CONTENT_DIRECTORY_TYPE = ServiceType.valueOf("urn:schemas-upnp-org:service:ContentDirectory:1");

	private static final long serialVersionUID = -3882119061427383748L;

	private final ServletCommon servletCommon;
	private final ContentTree contentTree;
	private final MediaDb mediaDb;
	private final UpnpService upnpService;
	private final RpcClient rpcClient;
	private final ImageResizer imageResizer;
	private final SearchEngine searchEngine;
	private final Supplier<Mustache> resultsTemplate;

	public SearchServlet(final ServletCommon servletCommon, final ContentTree contentTree, final MediaDb mediaDb, final UpnpService upnpService, final RpcClient rpcClient, final ImageResizer imageResizer) {
		this(servletCommon, contentTree, mediaDb, upnpService, rpcClient, imageResizer, new SearchEngine());
	}

	protected SearchServlet(final ServletCommon servletCommon, final ContentTree contentTree, final MediaDb mediaDb, final UpnpService upnpService, final RpcClient rpcClient, final ImageResizer imageResizer, final SearchEngine searchEngine) {
		this.servletCommon = servletCommon;
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
		this.rpcClient = rpcClient;
		this.upnpService = upnpService;
		this.imageResizer = imageResizer;
		this.searchEngine = searchEngine;
		this.resultsTemplate = servletCommon.mustacheTemplate("searchresults.html");
	}

	@SuppressWarnings("resource")
	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String query = StringUtils.trimToEmpty(req.getParameter(PARAM_QUERY));
		final PageScope pageScope = this.servletCommon.pageScope(req, StringUtils.defaultString(query, "Search"), null);

		if (!StringUtils.isBlank(query)) {
			final String username = ReqAttr.USERNAME.get(req);
			final String upnpQuery = String.format("(dc:title contains \"%s\")", query);

			try {
				final List<ContentItem> results;
				final Integer offset;
				final int nextLimit;
				final int nextOffset;
				if (this.mediaDb != null) {
					final Set<BigInteger> authIds = this.contentTree.getAuthSet().authIdsForUser(username);

					final Integer limit = ServletCommon.readIntParamWithDefault(req, resp, PARAM_PAGE_LIMIT, MAX_RESULTS, i -> i > 0);
					if (limit == null) return;
					offset = ServletCommon.readIntParamWithDefault(req, resp, PARAM_PAGE_OFFSET, 0, i -> i >= 0);
					if (offset == null) return;

					final List<String> ids = DbSearchParser.parseSearch(query, authIds).execute(this.mediaDb, limit, offset);
					results = this.contentTree.getItemsForIds(ids, username);
					nextLimit = limit;
					nextOffset = ids.size() >= limit ? offset + limit : 0;
				}
				else {
					final ContentNode rootNode = this.contentTree.getNode(ContentGroup.ROOT.getId());
					results = this.searchEngine.search(rootNode, upnpQuery, MAX_RESULTS, username);
					offset = null;
					nextLimit = MAX_RESULTS;  // Not implemented.
					nextOffset = 0;
				}

				final String linkQuery = "?" + PARAM_QUERY + "="
						+ StringEscapeUtils.escapeHtml4(UrlEscapers.urlFormParameterEscaper().escape(query));

				final String nextPagePath;
				if (nextOffset > 0) {
					nextPagePath = linkQuery + "&" + PARAM_PAGE_LIMIT + "=" + nextLimit + "&" + PARAM_PAGE_OFFSET + "=" + nextOffset;
				}
				else {
					nextPagePath = null;
				}

				final SearchResultsScope resultsScope = new SearchResultsScope(pageScope);
				final ResultGroupScope resultGroup = resultsScope.addResultGroup("Local items: " + results.size(), nextPagePath);
				appendItems(resultGroup, results, linkQuery, offset);

				// Only do remote search if local does not error.
				final String remote = StringUtils.trimToEmpty(req.getParameter(PARAM_REMOTE));
				if (ReqAttr.ALLOW_REMOTE_SEARCH.get(req) && StringUtils.isNotBlank(remote)) {
					// TODO Do these all in parallel.
					remoteSearch(upnpQuery, resultsScope);
					rpcSearch(query, resultsScope);
				}

				ServletCommon.setHtmlContentType(resp);
				this.resultsTemplate.get().execute(resp.getWriter(), new Object[] { pageScope, resultsScope }).flush();
			}
			catch (final Exception e) {
				ServletCommon.returnStatus(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to run query.");
				e.printStackTrace(resp.getWriter());  // TODO maybe do something better here...
			}
		}
	}

	private void appendItems(
			final ResultGroupScope resultGroup,
			final List<ContentItem> items,
			final String linkQuery,
			final Integer offset) throws IOException {

		int x = 0;
		for (final ContentItem i : items) {
			final String q = offset != null ? linkQuery + "&" + PARAM_PAGE_OFFSET + "=" + (offset + x) : linkQuery;
			resultGroup.addContentItem(i, q, this.imageResizer);
			x += 1;
		}
	}

	private void rpcSearch(final String query, final SearchResultsScope resultsScope) throws InterruptedException, ExecutionException {
		final Map<RemoteInstance, Future<SearchReply>> futures = new LinkedHashMap<>();
		for (final RemoteInstance ri : this.rpcClient.getRemoteInstances()) {
			final MediaFutureStub stub = this.rpcClient.getMediaFutureStub(ri.getId());
			final SearchRequest req = SearchRequest.newBuilder().setQuery(query).build();
			final ListenableFuture<SearchReply> f = stub.search(req);
			futures.put(ri, f);
		}

		for (final Entry<RemoteInstance, Future<SearchReply>> a : futures.entrySet()) {
			final SearchReply rep = a.getValue().get();
			final ResultGroupScope resultGroup = resultsScope.addResultGroup(a.getKey().getTarget() + " items: " + rep.getResultCount());
			for (final MediaItem i : rep.getResultList()) {
				final String path = C.REMOTE_CONTENT_PATH_PREFIX + a.getKey().getId() + "/" + i.getId();
				final long fileLength = i.getFileLength();
				final long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(i.getDurationMillis());
				resultGroup.addRemoteItem(
						path,
						i.getTitle(),
						fileLength > 0 ? FileHelper.readableFileSize(fileLength) : null,
						durationSeconds > 0 ? ModelUtil.toTimeString(durationSeconds) : null);
			}
		}
	}

	private void remoteSearch(final String searchCriteria, final SearchResultsScope resultsScope) throws InterruptedException, ExecutionException {
		final Collection<RemoteService> contentDirectoryServices = findAllContentDirectoryServices();
		searchContentDirectories(contentDirectoryServices, searchCriteria, resultsScope);
	}

	private Collection<RemoteService> findAllContentDirectoryServices() {
		final Collection<RemoteService> ret = new ArrayList<>();
		for (final RemoteDevice rd : this.upnpService.getRegistry().getRemoteDevices()) {
			ret.addAll(Arrays.asList(rd.findServices(CONTENT_DIRECTORY_TYPE)));
		}
		return ret;
	}

	private void searchContentDirectories(final Collection<RemoteService> contentDirectoryServices, final String searchCriteria, final SearchResultsScope resultsScope) throws InterruptedException, ExecutionException {
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
				resultsScope.addErrorGroup(title, err);
				continue;
			}

			final List<Item> items = s.getPayload().getItems();
			final ResultGroupScope resultGroup = resultsScope.addResultGroup(title + " items: " + items.size());
			for (final Item item : items) {
				final Res res = biggestRes(item.getResources());
				if (res == null) continue;

				final String size = res.getSize() != null ? FileHelper.readableFileSize(res.getSize()) : null;
				resultGroup.addRemoteItem(res.getValue(), item.getTitle(), size, res.getDuration());
			}
		}
	}

	private static Res biggestRes(final List<Res> resources) {
		Res ret = null;
		for (final Res r : resources) {
			if (ret == null || r.getSize() > ret.getSize()) ret = r;
		}
		return ret;
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
		public void received(final ActionInvocation<?> invocation, final DIDLContent didl) {
			this.payload.set(didl);
		}

		@Override
		public void updateStatus(final Status status) {
			// Unused.
		}
	}

}

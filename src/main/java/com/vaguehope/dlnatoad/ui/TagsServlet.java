package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vaguehope.dlnatoad.auth.Permission;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.Tag;
import com.vaguehope.dlnatoad.db.TagAutocompleter;
import com.vaguehope.dlnatoad.db.TagFrequency;
import com.vaguehope.dlnatoad.db.WritableMediaDb;
import com.vaguehope.dlnatoad.db.search.DbSearchSyntax;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.ContentTree;

public class TagsServlet extends HttpServlet {

	private static final String ACTION_GET = "gettags";
	private static final String ACTION_ADD = "addtag";
	private static final String ACTION_RM = "rmtag";

	private static final long serialVersionUID = -415230388463791737L;

	private final ContentTree contentTree;
	private final MediaDb mediaDb;
	private final TagAutocompleter tagAutocompleter;
	private final Gson gson;

	public TagsServlet(final ContentTree contentTree, final MediaDb mediaDb, final TagAutocompleter tagAutocompleter) {
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
		this.tagAutocompleter = tagAutocompleter;
		this.gson = new GsonBuilder().create();
	}

	@SuppressWarnings("resource")
	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (this.mediaDb == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Can not store tags when --db is not specified.");
			return;
		}

		final TagsRequest tagReq = this.gson.fromJson(req.getReader(), TagsRequest.class);
		try {
			if (ACTION_GET.equals(tagReq.action)) {
				doGetTags(req, resp, tagReq);
			}
			else if (ACTION_ADD.equals(tagReq.action) || ACTION_RM.equals(tagReq.action)) {
				doWriteTag(req, resp, tagReq);
			}
			else {
				ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid action.");
			}
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}
	}

	private void doGetTags(final HttpServletRequest req, final HttpServletResponse resp, final TagsRequest tagReq) throws IOException, SQLException {
		final List<ContentItem> items = readReqItems(req, resp, tagReq);
		if (items == null) return;

		returnTagsAndCounts(resp, items);
	}

	private void doWriteTag(final HttpServletRequest req, final HttpServletResponse resp, final TagsRequest tagReq) throws IOException, SQLException {
		if (!ReqAttr.ALLOW_EDIT_TAGS.get(req)) {
			ServletCommon.returnForbidden(resp);
			return;
		}

		final String tag = StringUtils.trimToNull(tagReq.tag);
		if (tagReq.tag == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid tag.");
			return;
		}

		final boolean remove = ACTION_RM.equals(tagReq.action);
		if (remove && tagReq.cls == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Must specify cls on rm.");
			return;
		}
		if (!remove && tagReq.cls != null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Can not specify cls on add.");
			return;
		}

		final List<ContentItem> items = readReqItems(req, resp, tagReq);
		if (items == null) return;

		final Set<ContentNode> nodes = new HashSet<>();
		for (final ContentItem i : items) {
			final ContentNode node = this.contentTree.getNode(i.getParentId());
			if (node == null) {
				ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid item.");
				return;
			}
			nodes.add(node);
		}

		final String username = ReqAttr.USERNAME.get(req);
		for (final ContentNode node : nodes) {
			if (node.hasAuthList() && !node.isUserAuthWithPermission(username, Permission.EDITTAGS)) {
				ServletCommon.returnDenied(resp, username);
				return;
			}
		}

		final long now = System.currentTimeMillis();
		try (final WritableMediaDb w = this.mediaDb.getWritable()) {
			for (final ContentItem i : items) {
				if (remove) {
					w.setTagModifiedAndDeleted(i.getId(), tag, tagReq.cls, true, now);
				}
				else {
					w.addTag(i.getId(), tag, now);
				}
			}
		}

		int count = items.size();
		if (remove) count = 0 - count;
		this.tagAutocompleter.changeTagCount(tag, count);

		returnTagsAndCounts(resp, items);
	}

	private List<ContentItem> readReqItems(final HttpServletRequest req, final HttpServletResponse resp, final TagsRequest tagReq) throws IOException {
		final String username = ReqAttr.USERNAME.get(req);
		final List<ContentItem> items = this.contentTree.getItemsForIds(tagReq.ids, username);
		if (tagReq.ids.size() != items.size()) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "One or more specified IDs are invalid.");
			return null;
		}
		return items;
	}

	@SuppressWarnings("resource")
	private void returnTagsAndCounts(final HttpServletResponse resp, final List<ContentItem> items) throws SQLException, IOException {
		final Multiset<Tag> tagCounts = HashMultiset.create();
		for (final ContentItem i : items) {
			for (final Tag t : this.mediaDb.getTags(i.getId(), false, false)) {
				tagCounts.add(new Tag(t.getTag(), t.getCls(), 0L, false));
			}
		}
		final List<TagFrequency> ret = new ArrayList<>();
		for (final Entry<Tag> e : tagCounts.entrySet()) {
			ret.add(new TagFrequencyUi(e.getElement().getTag(), e.getElement().getCls(), e.getCount()));
		}
		ret.sort(TagFrequency.Order.COUNT_DESC);

		resp.setContentType(ServletCommon.CONTENT_TYPE_JSON);
		this.gson.toJson(ret, resp.getWriter());
	}

	private static class TagsRequest {
		String action;
		String tag;
		String cls;
		List<String> ids;
	}


	public class TagFrequencyUi extends TagFrequency {
		private final String search;

		public TagFrequencyUi(String tag, String cls, int count) {
			super(tag, cls, count);
			this.search = DbSearchSyntax.makeSingleTagSearch(tag);
		}
	}

}

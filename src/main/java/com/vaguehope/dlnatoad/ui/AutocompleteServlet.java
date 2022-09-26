package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.TagFrequency;

public class AutocompleteServlet extends HttpServlet {

	private static final int MAX_SUGGESTIONS = 20;
	private static final String CONTENT_TYPE_JSON = "text/json;charset=utf-8";
	private static final long serialVersionUID = 7357804711012837077L;

	private final MediaDb mediaDb;
	private final Gson gson;

	public AutocompleteServlet(final MediaDb mediaDb) {
		this.mediaDb = mediaDb;
		this.gson = new GsonBuilder().create();
	}

	@SuppressWarnings("resource")
	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (this.mediaDb == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "DB is not enabled.");
			return;
		}

		final String mode = ServletCommon.readRequiredParam(req, resp, "mode", 1);
		if (mode == null) return;
		final String fragment = ServletCommon.readRequiredParam(req, resp, "fragment", 1);
		if (fragment == null) return;

		final List<TagFrequency> tags;
		if ("addtag".equalsIgnoreCase(mode)) {
			tags = forAddTag(fragment);
		}
		else if ("search".equalsIgnoreCase(mode)) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "TODO: implement search suggestions.");
			return;
		}
		else {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid mode.");
			return;
		}

		resp.setContentType(CONTENT_TYPE_JSON);
		this.gson.toJson(tags, resp.getWriter());
	}

	private List<TagFrequency> forAddTag(final String fragment) throws IOException {
		try {
			// TODO there is probably a way to push this down to the DB layer, but this will do for now.
			final List<TagFrequency> ret = this.mediaDb.getAutocompleteSuggestions(fragment, MAX_SUGGESTIONS, true);
			if (ret.size() < MAX_SUGGESTIONS) {
				final List<TagFrequency> moreTags = this.mediaDb.getAutocompleteSuggestions(fragment, ret.size() + MAX_SUGGESTIONS, false);
				if (moreTags.size() > 0) {
					final Set<String> prevTags = setOfTags(ret);
					for (final TagFrequency t : moreTags) {
						if (!prevTags.contains(t.getTag())) {
							ret.add(t);
						}
					}
				}
			}
			return ret;
		}
		catch (final SQLException e) {
			throw new IOException(e);
		}
	}

	private static Set<String> setOfTags(final List<TagFrequency> tags) {
		final Set<String> ret = new HashSet<>();
		for (final TagFrequency t : tags) {
			ret.add(t.getTag());
		}
		return ret;
	}

}

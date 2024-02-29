package com.vaguehope.dlnatoad.ui.templates;

public class PageScope {

	public final String page_title;
	public final String path_prefix;
	public final String username;
	public final boolean db_enabled;
	public final String query;
	public final boolean allow_remote_search;

	public PageScope(final String page_title, final String path_prefix, final String username, final boolean db_enabled, final String query, final boolean allow_remote_search) {
		this.page_title = page_title;
		this.path_prefix = path_prefix;
		this.username = username;
		this.db_enabled = db_enabled;
		this.query = query;
		this.allow_remote_search = allow_remote_search;
	}

}

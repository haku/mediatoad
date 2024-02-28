package com.vaguehope.dlnatoad.ui.templates;

public class PageScope {

	public final String page_title;
	public final String path_prefix;
	public final String username;
	public final boolean db_enabled;
	public final boolean allow_remote_search;

	public PageScope(String page_title, String path_prefix, String username, boolean db_enabled, boolean allow_remote_search) {
		this.page_title = page_title;
		this.path_prefix = path_prefix;
		this.username = username;
		this.db_enabled = db_enabled;
		this.allow_remote_search = allow_remote_search;
	}

}

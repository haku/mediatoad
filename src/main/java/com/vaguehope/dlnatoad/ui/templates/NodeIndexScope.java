package com.vaguehope.dlnatoad.ui.templates;

public class NodeIndexScope {

	public final ResultGroupScope favourites;
	public final ResultGroupScope results;
	public final boolean show_list_link_row;
	public final boolean show_prefs;
	public final boolean is_favourite;
	public final boolean is_sort_modified;
	public final boolean is_video_thumbs;
	public final String node_id;
	public final String node_file_name;
	public final String node_total_size;

	public NodeIndexScope(
			final ResultGroupScope favourites,
			final ResultGroupScope results,
			final boolean show_list_link_row,
			final boolean show_prefs,
			final boolean is_favourite,
			final boolean is_sort_modified,
			final boolean is_video_thumbs,
			final String node_id,
			final String node_file_name,
			final String node_total_size) {
		this.node_id = node_id;
		this.show_list_link_row = show_list_link_row;
		this.show_prefs = show_prefs;
		this.is_favourite = is_favourite;
		this.is_sort_modified = is_sort_modified;
		this.is_video_thumbs = is_video_thumbs;
		this.node_file_name = node_file_name;
		this.node_total_size = node_total_size;
		this.favourites = favourites;
		this.results = results;
	}

}

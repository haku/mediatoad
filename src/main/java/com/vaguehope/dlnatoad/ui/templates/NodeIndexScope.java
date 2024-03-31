package com.vaguehope.dlnatoad.ui.templates;

public class NodeIndexScope {

	public final ResultGroupScope results;
	public final String up_link_path;
	public boolean show_list_link_row;
	public final String node_id;
	public final String node_file_name;
	public final String node_total_size;

	public NodeIndexScope(
			final ResultGroupScope results,
			final String up_link_path,
			final boolean show_list_link_row,
			final String node_id,
			final String node_file_name,
			final String node_total_size) {
		this.node_id = node_id;
		this.up_link_path = up_link_path;
		this.show_list_link_row = show_list_link_row;
		this.node_file_name = node_file_name;
		this.node_total_size = node_total_size;
		this.results = results;
	}

}

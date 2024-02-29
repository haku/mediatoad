package com.vaguehope.dlnatoad.ui.templates;

import java.util.ArrayList;
import java.util.List;

public class SearchResultsScope {

	public final List<ResultGroupScope> result_groups = new ArrayList<>();

	private final PageScope pageScope;

	public SearchResultsScope(final PageScope pageScope) {
		this.pageScope = pageScope;
	}

	public ResultGroupScope addResultGroup(final String group_title) {
		return addResultGroup(group_title, null);
	}

	public ResultGroupScope addResultGroup(final String group_title, final String next_page_path) {
		final ResultGroupScope group = new ResultGroupScope(group_title, null, next_page_path, this.pageScope);
		this.result_groups.add(group);
		return group;
	}

	public void addErrorGroup(final String group_title, final String msg) {
		this.result_groups.add(new ResultGroupScope(group_title, msg, null, this.pageScope));
	}

}

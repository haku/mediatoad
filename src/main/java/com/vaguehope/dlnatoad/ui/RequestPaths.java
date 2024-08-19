package com.vaguehope.dlnatoad.ui;

import java.util.Objects;

import com.vaguehope.dlnatoad.C;

public class RequestPaths {

	private static final String SEARCH_PATH_PREFIX = "/" + C.SEARCH_PATH_PREFIX;
	private static final SearchPath EMPTY_SEARCH_PATH = new SearchPath("", "");

	public static SearchPath parseSearchPath(final String pathInfo) {
		if (pathInfo == null || pathInfo.length() < 1) return EMPTY_SEARCH_PATH;

		final String subPath;
		if (pathInfo.startsWith(SEARCH_PATH_PREFIX)) {
			subPath = pathInfo.substring(SEARCH_PATH_PREFIX.length());
		}
		else if (pathInfo.startsWith("/")) {
			subPath = pathInfo.substring(1);
		}
		else {
			subPath = pathInfo;
		}

		final String query = firstDirFromPath(subPath);
		final String file = removePrefixOrEmpty(subPath, query + "/");
		return new SearchPath(query, file);
	}

	private static String firstDirFromPath(final String path) {
		if (path == null) return "";

		final int x = path.indexOf('/');
		if (x < 0) return path;
		return path.substring(0, x);
	}

	private static String removePrefixOrEmpty(final String s, final String prefix) {
		if (s == null || !s.startsWith(prefix)) return "";
		return s.substring(prefix.length());
	}

	public static class SearchPath {
		public final String query;
		public final String file;

		public SearchPath(final String query, final String file) {
			this.query = query;
			this.file = file;
		}

		@Override
		public String toString() {
			return String.format("SearchPath{%s, %s}", this.query, this.file);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.query, this.file);
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == null) return false;
			if (this == obj) return true;
			if (!(obj instanceof SearchPath)) return false;
			final SearchPath that = (SearchPath) obj;
			return Objects.equals(this.query, that.query)
					&& Objects.equals(this.file, that.file);
		}
	}

}

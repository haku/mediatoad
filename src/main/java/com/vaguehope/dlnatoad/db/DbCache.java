package com.vaguehope.dlnatoad.db;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class DbCache {

	private static final int TOP_TAG_COUNT = 100; // TODO make cache param? sublist cache entries?

	private final MediaDb db;

	private final Cache<TopTagsKey, List<TagFrequency>> topTags = CacheBuilder.newBuilder()
			.expireAfterAccess(1, TimeUnit.DAYS)
			.build();

	public DbCache(final MediaDb db) {
		this.db = db;
	}

	public List<TagFrequency> getTopTags(final Set<BigInteger> authIds, final String pathPrefix) throws SQLException {
		try {
			return this.topTags.get(
					new TopTagsKey(authIds, pathPrefix),
					() -> this.db.getTopTags(authIds, pathPrefix, TOP_TAG_COUNT));
		}
		catch (final ExecutionException e) {
			if (e.getCause() instanceof SQLException) {
				throw (SQLException) e.getCause();
			}
			throw new IllegalStateException(e);
		}
	}

	private static class TopTagsKey {

		final Set<BigInteger> authIds;
		final String pathPrefix;

		public TopTagsKey(final Set<BigInteger> authIds, final String pathPrefix) {
			this.authIds = authIds;
			this.pathPrefix = pathPrefix;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.authIds, this.pathPrefix);
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == null) return false;
			if (this == obj) return true;
			if (!(obj instanceof TopTagsKey)) return false;
			final TopTagsKey that = (TopTagsKey) obj;
			return Objects.equals(this.authIds, that.authIds)
					&& Objects.equals(this.pathPrefix, that.pathPrefix);
		}

	}

}

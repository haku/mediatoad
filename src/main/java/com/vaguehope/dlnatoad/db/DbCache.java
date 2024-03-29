package com.vaguehope.dlnatoad.db;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class DbCache {

	private static final int TOP_TAG_COUNT = 100; // TODO make cache param? sublist cache entries?

	private final MediaDb db;
	private final LoadingCache<TopTagsKey, ValueAndVersion<List<TagFrequency>>> topTags;
	private final CacheLoader<TopTagsKey, ValueAndVersion<List<TagFrequency>>> topTagLoader = new TopTagLoader();

	public DbCache(final MediaDb db) {
		this(db, Ticker.systemTicker());
	}

	DbCache(final MediaDb db, final Ticker ticker) {
		this.db = db;
		this.topTags = CacheBuilder.newBuilder()
				.refreshAfterWrite(5, TimeUnit.MINUTES)
				.expireAfterWrite(1, TimeUnit.DAYS)
				.ticker(ticker)
				.build(this.topTagLoader);
	}

	public List<TagFrequency> getTopTags(final Set<BigInteger> authIds, final String pathPrefix) throws SQLException {
		try {
			final ValueAndVersion<List<TagFrequency>> val = this.topTags.get(new TopTagsKey(authIds, pathPrefix));
			return val == null ? null : val.value;
		}
		catch (final ExecutionException e) {
			if (e.getCause() instanceof SQLException) {
				throw (SQLException) e.getCause();
			}
			throw new IllegalStateException(e);
		}
	}

	private class TopTagLoader extends CacheLoader<TopTagsKey, ValueAndVersion<List<TagFrequency>>> {
		@Override
		public ValueAndVersion<List<TagFrequency>> load(final TopTagsKey key) throws Exception {
			final long ver = DbCache.this.db.getWriteCount();
			final List<TagFrequency> tags = DbCache.this.db.getTopTags(key.authIds, key.pathPrefix, TOP_TAG_COUNT);
			return new ValueAndVersion<>(tags, ver);
		}

		@Override
		public ListenableFuture<ValueAndVersion<List<TagFrequency>>> reload(final TopTagsKey key, final ValueAndVersion<List<TagFrequency>> oldValue) throws Exception {
			if (oldValue.version == DbCache.this.db.getWriteCount()) return Futures.immediateFuture(oldValue);
			return super.reload(key, oldValue);
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

	private static class ValueAndVersion<T> {
		final T value;
		final long version;

		public ValueAndVersion(final T value, final long version) {
			this.value = value;
			this.version = version;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.value, this.version);
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == null) return false;
			if (this == obj) return true;
			if (!(obj instanceof ValueAndVersion)) return false;
			final ValueAndVersion<?> that = (ValueAndVersion<?>) obj;
			return Objects.equals(this.value, that.value)
					&& Objects.equals(this.version, that.version);
		}

	}

}

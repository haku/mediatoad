package com.vaguehope.dlnatoad.db;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.vaguehope.dlnatoad.db.search.DbSearchParser;

public class DbCache {

	private static final int TOP_TAG_COUNT = 200; // TODO make cache param? sublist cache entries?
	private static final Logger LOG = LoggerFactory.getLogger(DbCache.class);

	private final MediaDb db;
	private final Executor executor;
	private final boolean verboseLog;

	private final LoadingCache<CacheKey, ValueAndVersion<List<TagFrequency>>> dirTopTags;
	private final LoadingCache<CacheKey, ValueAndVersion<List<TagFrequency>>> searchTopTags;

	public DbCache(final MediaDb db, final Executor executor, final boolean verboseLog) {
		this(db, executor, verboseLog, Ticker.systemTicker());
	}

	DbCache(final MediaDb db, final Executor executor, final boolean verboseLog, final Ticker ticker) {
		this.db = db;
		this.executor = executor;
		this.verboseLog = verboseLog;
		this.dirTopTags = CacheBuilder.newBuilder()
				.maximumSize(1000L)
				.refreshAfterWrite(5, TimeUnit.MINUTES)
				.expireAfterWrite(5, TimeUnit.DAYS)
				.ticker(ticker)
				.build(new DirTopTagLoader());
		this.searchTopTags = CacheBuilder.newBuilder()
				.maximumSize(1000L)
				.refreshAfterWrite(5, TimeUnit.MINUTES)
				.expireAfterWrite(5, TimeUnit.DAYS)
				.ticker(ticker)
				.build(new SearchTopTagLoader());
	}

	public List<TagFrequency> dirTopTags(final Set<BigInteger> authIds, final String pathPrefix) throws SQLException {
		return readCache(this.dirTopTags, new CacheKey(authIds, pathPrefix));
	}

	public List<TagFrequency> searchTopTags(final Set<BigInteger> authIds, final String query) throws SQLException {
		return readCache(this.searchTopTags, new CacheKey(authIds, query));
	}

	private static <T> T readCache(final LoadingCache<CacheKey, ValueAndVersion<T>> cache, final CacheKey key) throws SQLException {
		try {
			final ValueAndVersion<T> val = cache.get(key);
			return val == null ? null : val.value;
		}
		catch (final ExecutionException e) {
			if (e.getCause() instanceof SQLException) {
				throw (SQLException) e.getCause();
			}
			throw new IllegalStateException(e);
		}
	}

	private class DirTopTagLoader extends DbLoader<List<TagFrequency>> {
		@Override
		public ValueAndVersion<List<TagFrequency>> load(final CacheKey key) throws Exception {
			final long ver = DbCache.this.db.getWriteCount();
			final List<TagFrequency> tags = DbCache.this.db.getTopTags(key.authIds, key.query, TOP_TAG_COUNT);
			return new ValueAndVersion<>(tags, ver);
		}
	}

	private class SearchTopTagLoader extends DbLoader<List<TagFrequency>> {
		@Override
		public ValueAndVersion<List<TagFrequency>> load(final CacheKey key) throws Exception {
			final long ver = DbCache.this.db.getWriteCount();
			final List<TagFrequency> tags = DbSearchParser.parseSearchForTags(key.query, key.authIds).execute(DbCache.this.db, TOP_TAG_COUNT, 0);
			return new ValueAndVersion<>(tags, ver);
		}
	}

	private abstract class DbLoader<T> extends CacheLoader<CacheKey, ValueAndVersion<T>> {
		@Override
		public ListenableFuture<ValueAndVersion<T>> reload(final CacheKey key, final ValueAndVersion<T> oldValue) throws Exception {
			if (oldValue.version == DbCache.this.db.getWriteCount()) return Futures.immediateFuture(oldValue);
			if (DbCache.this.verboseLog) LOG.info("Scheduled background cache refresh for: {}", key);
			return Futures.submit(() -> load(key), DbCache.this.executor);
		}
	}

	private static class CacheKey {
		final Set<BigInteger> authIds;
		final String query;

		public CacheKey(final Set<BigInteger> authIds, final String query) {
			this.authIds = authIds;
			this.query = query;
		}

		@Override
		public String toString() {
			return String.format("CacheKey{%s, %s}", this.authIds, this.query);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.authIds, this.query);
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == null) return false;
			if (this == obj) return true;
			if (!(obj instanceof CacheKey)) return false;
			final CacheKey that = (CacheKey) obj;
			return Objects.equals(this.authIds, that.authIds)
					&& Objects.equals(this.query, that.query);
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

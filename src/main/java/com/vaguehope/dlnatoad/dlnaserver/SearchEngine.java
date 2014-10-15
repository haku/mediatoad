package com.vaguehope.dlnatoad.dlnaserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.support.contentdirectory.ContentDirectoryException;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.AudioItem;
import org.teleal.cling.support.model.item.ImageItem;
import org.teleal.cling.support.model.item.Item;
import org.teleal.cling.support.model.item.VideoItem;

import com.vaguehope.cdsc.CDSCBaseListener;
import com.vaguehope.cdsc.CDSCLexer;
import com.vaguehope.cdsc.CDSCParser;
import com.vaguehope.cdsc.CDSCParser.RelExpContext;
import com.vaguehope.dlnatoad.util.StringHelper;

public class SearchEngine {

	private static final Logger LOG = LoggerFactory.getLogger(SearchEngine.class);

	public SearchEngine () {}

	public List<Item> search (final ContentNode contentNode, final String searchCriteria) throws ContentDirectoryException {
		final long startTime = System.nanoTime();
		final Predicate<Item> predicate = criteriaToPredicate(searchCriteria);
		if (predicate == null) throw new ContentDirectoryException(ContentDirectoryErrorCodes.UNSUPPORTED_SEARCH_CRITERIA, "Do not know how to parse: " + searchCriteria);
		LOG.debug("'{}' => {} in {}ms.", searchCriteria, predicate, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
		return filterItems(contentNode.getContainer(), predicate);
	}

	protected static Predicate<Item> criteriaToPredicate (final String searchCriteria) {
		final CriteriaListener listener = new CriteriaListener();
		new ParseTreeWalker().walk(listener, new CDSCParser(
				new CommonTokenStream(new CDSCLexer(new ANTLRInputStream(searchCriteria)))
				).searchCrit());
		return listener.getPredicate();
	}

	private static class CriteriaListener extends CDSCBaseListener {

		private static final Set<String> TITLE_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
				"dc:title", "dc:creator", "upnp:artist")));

		@SuppressWarnings("serial") private static final Map<String, Class<?>> UPNP_TO_CLING = Collections.unmodifiableMap(new HashMap<String, Class<?>>() {
			{
				put(VideoItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), VideoItem.class);
				put(AudioItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), AudioItem.class);
				put(ImageItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), ImageItem.class);
			}
		});

		private final Set<Predicate<Item>> allPredicates = new LinkedHashSet<>();

		public CriteriaListener () {}

		public Predicate<Item> getPredicate () {
			if (this.allPredicates == null || this.allPredicates.size() < 1) return Bool.FALSE;
			return new And<>(this.allPredicates);
		}

		@Override
		public void enterRelExp (@NotNull final RelExpContext ctx) {
			final String propertyName = ctx.Property().getText();
			final String op = ctx.binOp().getText();
			final String value = StringHelper.unquoteQuotes(ctx.QuotedVal().getText());

			if ("upnp:class".equals(propertyName)) {
				if ("=".equals(op) || "derivedfrom".equalsIgnoreCase(op)) {
					relExpDerivedfrom(propertyName, value);
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
				}
			}
			else if (TITLE_FIELDS.contains(propertyName)) {
				if ("=".equals(op) || "contains".equalsIgnoreCase(op)) {
					this.allPredicates.add(new TitleContains(value));
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
				}
			}
			else {
				LOG.debug("Unsupported property: {}", propertyName);
			}
		}

		private void relExpDerivedfrom (final String propertyName, final String value) {
			final String lcastUpnpClass = value.toLowerCase(Locale.ENGLISH);
			for (final Entry<String, Class<?>> utc : UPNP_TO_CLING.entrySet()) {
				if (lcastUpnpClass.startsWith(utc.getKey())) {
					this.allPredicates.add(new ClassInstanceOf<Item>(utc.getValue()));
					return;
				}
			}
			LOG.debug("Unsupported value for property {}: {}", propertyName, value);
		}

	}

	/**
	 * Lazy recursive impl.
	 */
	private static List<Item> filterItems (final Container container, final Predicate<Item> predicate) {
		final List<Item> results = new ArrayList<>();
		for (final Item ci : container.getItems()) {
			if (predicate.matches(ci)) results.add(ci);
		}
		if (container.getContainers() != null) {
			for (final Container childContainer : container.getContainers()) {
				results.addAll(filterItems(childContainer, predicate));
			}
		}
		return results;
	}

	protected interface Predicate<T> {
		boolean matches (T thing);
	}

	private enum Bool implements Predicate {
		TRUE(true),
		FALSE(false);

		private final boolean v;

		private Bool (final boolean v) {
			this.v = v;
		}

		@Override
		public boolean matches (final Object thing) {
			return this.v;
		}
	}

	private static class And<T> implements Predicate<T> {

		private final Collection<Predicate<T>> predicates;

		public And (final Collection<Predicate<T>> predicates) {
			this.predicates = predicates;
		}

		@Override
		public boolean matches (final T thing) {
			for (final Predicate<T> p : this.predicates) {
				if (!p.matches(thing)) return false;
			}
			return true;
		}

		@Override
		public String toString () {
			return StringHelper.join(this.predicates, " and ");
		}

		@Override
		public int hashCode () {
			return this.predicates.hashCode();
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (!(obj instanceof And)) return false;
			final And<?> that = (And<?>) obj;
			return Objects.equals(this.predicates, that.predicates);
		}

	}

	private static class ClassInstanceOf<T> implements Predicate<T> {

		private final Class<?> cls;

		public ClassInstanceOf (final Class<?> cls) {
			this.cls = cls;
		}

		@Override
		public boolean matches (final T item) {
			return this.cls.isAssignableFrom(item.getClass());
		}

		@Override
		public String toString () {
			return String.format("instanceOf %s", this.cls.getSimpleName());
		}

		@Override
		public int hashCode () {
			return this.cls.hashCode();
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (!(obj instanceof ClassInstanceOf)) return false;
			final ClassInstanceOf<?> that = (ClassInstanceOf<?>) obj;
			return Objects.equals(this.cls, that.cls);
		}

	}

	private static class TitleContains implements Predicate<Item> {

		private final String lcaseSubString;

		public TitleContains (final String subString) {
			this.lcaseSubString = subString.toLowerCase(Locale.ENGLISH);
		}

		@Override
		public boolean matches (final Item item) {
			return item.getTitle().toLowerCase(Locale.ENGLISH).contains(this.lcaseSubString);
		}

		@Override
		public String toString () {
			return String.format("titleContains '%s'", this.lcaseSubString);
		}

		@Override
		public int hashCode () {
			return this.lcaseSubString.hashCode();
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (!(obj instanceof TitleContains)) return false;
			final TitleContains that = (TitleContains) obj;
			return Objects.equals(this.lcaseSubString, that.lcaseSubString);
		}

	}

}

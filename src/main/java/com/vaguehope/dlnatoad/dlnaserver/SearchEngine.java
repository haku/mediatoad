package com.vaguehope.dlnatoad.dlnaserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.DIDLObject.Property;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.AudioItem;
import org.fourthline.cling.support.model.item.ImageItem;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.VideoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.cdsc.CDSCBaseListener;
import com.vaguehope.cdsc.CDSCLexer;
import com.vaguehope.cdsc.CDSCParser;
import com.vaguehope.cdsc.CDSCParser.LogOpContext;
import com.vaguehope.cdsc.CDSCParser.RelExpContext;
import com.vaguehope.cdsc.CDSCParser.SearchExpContext;
import com.vaguehope.dlnatoad.util.StringHelper;

public class SearchEngine {

	private static final Logger LOG = LoggerFactory.getLogger(SearchEngine.class);

	public SearchEngine () {}

	public List<Item> search (final ContentNode contentNode, final String searchCriteria) throws ContentDirectoryException {
		final long startTime = System.nanoTime();
		final Predicate<Item> predicate = criteriaToPredicate(searchCriteria);
		if (predicate == null) throw new ContentDirectoryException(ContentDirectoryErrorCodes.UNSUPPORTED_SEARCH_CRITERIA, "Do not know how to parse: " + searchCriteria);
		LOG.debug("'{}' => {} in {}ms.", searchCriteria, predicate, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));

		// FIXME TODO This breaks the locking inside ContentNode and only works because
		// filterItems() does its own locking on the container object.
		final Container container = contentNode.applyContainer(c -> c);

		return filterItems(container, predicate);
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
				"dc:title")));

		private static final Set<String> ARTIST_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
				"dc:creator", "upnp:artist")));

		@SuppressWarnings("serial") private static final Map<String, Class<?>> UPNP_TO_CLING = Collections.unmodifiableMap(new HashMap<String, Class<?>>() {
			{
				put(VideoItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), VideoItem.class);
				put(AudioItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), AudioItem.class);
				put(ImageItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), ImageItem.class);
			}
		});

		private static class Exp {
			Predicate<Item> left;
			LogOp logOp;
			Predicate<Item> right;

			public Exp () {}

			@Override
			public String toString () {
				return String.format("{%s %s %s}", this.left, this.logOp, this.right);
			}
		}

		private final LinkedList<Exp> stack = new LinkedList<Exp>();
		private Exp cExp = new Exp();

		public CriteriaListener () {}

		public Predicate<Item> getPredicate () {
			if (this.stack.size() > 0) throw new IllegalStateException("Items left over on stack: " + this.stack);
			if (this.cExp.left == null) throw new IllegalStateException("Left is null: " + this.cExp);
			if (this.cExp.logOp != null) throw new IllegalStateException("Op not null: " + this.cExp);
			if (this.cExp.right != null) throw new IllegalStateException("Right not null: " + this.cExp);
			return this.cExp.left;
		}

		private static Predicate<Item> collapse (final Exp exp) {
			if (exp.left == null) throw new IllegalStateException("Left is null: " + exp);
			if (exp.logOp == null) throw new IllegalStateException("Op is null: " + exp);
			if (exp.right == null) throw new IllegalStateException("Right is null: " + exp);

			switch (exp.logOp) {
				case AND:
					return new And<Item>(Arrays.asList(exp.left, exp.right));
				case OR:
					return new Or<Item>(Arrays.asList(exp.left, exp.right));
				default:
					throw new IllegalStateException("Unknown op: " + exp.logOp);
			}
		}

		@Override
		public void enterSearchExp (final SearchExpContext ctx) {
			if (!ctx.getText().startsWith("(")) return;

			if (this.cExp.left != null) {
				this.stack.push(this.cExp);
				this.cExp = new Exp();
			}
		}

		@Override
		public void exitSearchExp (final SearchExpContext ctx) {
			if (!ctx.getText().endsWith(")")) return;

			final Exp poped = this.stack.pollLast();
			if (poped != null) {
				if (poped.right != null) throw new IllegalStateException("Poped right not null: " + poped);
				poped.right = collapse(this.cExp);
				this.cExp = poped;
			}
			else if (this.cExp.right != null) { // Two closes in a row, )), means nothing to collapse.
				this.cExp.left = collapse(this.cExp);
				this.cExp.logOp = null;
				this.cExp.right = null;
			}
		}

		@Override
		public void exitLogOp (final LogOpContext ctx) {
			if (this.cExp.left == null) throw new IllegalStateException("Left is null: " + this.cExp.left);

			if (this.cExp.right != null) { // Already got a right means second or in "a or b or c", so collapse "a or b".
				this.cExp.left = collapse(this.cExp);
				this.cExp.logOp = null;
				this.cExp.right = null;
			}

			if ("or".equals(ctx.getText())) {
				this.cExp.logOp = LogOp.OR;
			}
			else if ("and".equals(ctx.getText())) {
				this.cExp.logOp = LogOp.AND;
			}
			else {
				throw new IllegalArgumentException("Unknown LogOp: " + ctx.getText());
			}
		}

		@Override
		public void exitRelExp (final RelExpContext ctx) {
			final String propertyName = ctx.Property().getText();
			final String op = ctx.binOp().getText();
			final String value = StringHelper.unquoteQuotes(ctx.QuotedVal().getText());

			final Predicate<Item> predicate;

			if ("upnp:class".equals(propertyName)) {
				if ("=".equals(op) || "derivedfrom".equalsIgnoreCase(op)) {
					predicate = relExpDerivedfrom(propertyName, value);
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
					predicate = Bool.TRUE;
				}
			}
			else if (TITLE_FIELDS.contains(propertyName)) {
				if ("=".equals(op) || "contains".equalsIgnoreCase(op)) {
					predicate = new TitleContains(value);
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
					predicate = Bool.TRUE;
				}
			}
			else if (ARTIST_FIELDS.contains(propertyName)) {
				if ("=".equals(op) || "contains".equalsIgnoreCase(op)) {
					predicate = new ArtistContains(value);
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
					predicate = Bool.TRUE;
				}
			}
			else {
				LOG.debug("Unsupported property: {}", propertyName);
				predicate = Bool.TRUE;
			}

			if (this.cExp.left == null) {
				this.cExp.left = predicate;
			}
			else if (this.cExp.right == null) {
				this.cExp.right = predicate;
			}
			else {
				throw new IllegalStateException("Neither left or right are null: " + this.cExp);
			}

		}

		private static Predicate<Item> relExpDerivedfrom (final String propertyName, final String value) {
			final String lcastUpnpClass = value.toLowerCase(Locale.ENGLISH);
			for (final Entry<String, Class<?>> utc : UPNP_TO_CLING.entrySet()) {
				if (lcastUpnpClass.startsWith(utc.getKey())) {
					return new ClassInstanceOf<Item>(utc.getValue());
				}
			}
			LOG.debug("Unsupported value for property {}: {}", propertyName, value);
			return Bool.TRUE;
		}

	}

	/**
	 * Lazy recursive impl.
	 */
	private static List<Item> filterItems (final Container container, final Predicate<Item> predicate) {
		final List<Item> results = new ArrayList<>();
		synchronized (container) {
			for (final Item ci : container.getItems()) {
				if (predicate.matches(ci)) results.add(ci);
			}
			for (final Container childContainer : container.getContainers()) {
				if (ContentGroup.RECENT.getId().equals(childContainer.getId())) continue;  // Do not search in recent.
				results.addAll(filterItems(childContainer, predicate));
			}
		}
		return results;
	}

	private enum LogOp {
		OR, AND
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

	private static class Or<T> implements Predicate<T> {

		private final Collection<Predicate<T>> predicates;

		public Or (final Collection<Predicate<T>> predicates) {
			this.predicates = predicates;
		}

		@Override
		public boolean matches (final T thing) {
			for (final Predicate<T> p : this.predicates) {
				if (p.matches(thing)) return true;
			}
			return false;
		}

		@Override
		public String toString () {
			return StringHelper.join("(", ")", this.predicates, " or ");
		}

		@Override
		public int hashCode () {
			return this.predicates.hashCode();
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (!(obj instanceof Or)) return false;
			final Or<?> that = (Or<?>) obj;
			return Objects.equals(this.predicates, that.predicates);
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
			return StringHelper.join("(", ")", this.predicates, " and ");
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

	private static class ArtistContains implements Predicate<Item> {

		/**
		 * See constructor of org.teleal.cling.support.model.DIDLObject.Property.
		 */
		private static final String ARTIST_DES_NAME = DIDLObject.Property.UPNP.ARTIST.class.getSimpleName().toLowerCase();

		private final String lcaseSubString;

		public ArtistContains (final String subString) {
			this.lcaseSubString = subString.toLowerCase(Locale.ENGLISH);
		}

		@Override
		public boolean matches (final Item item) {
			for (final Property<?> prop : item.getProperties()) {
				if (ARTIST_DES_NAME.equals(prop.getDescriptorName())) {
					if (prop.getValue().toString().toLowerCase(Locale.ENGLISH).contains(this.lcaseSubString)) return true;
				}
			}
			return false;
		}

		@Override
		public String toString () {
			return String.format("artistContains '%s'", this.lcaseSubString);
		}

		@Override
		public int hashCode () {
			return this.lcaseSubString.hashCode();
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (!(obj instanceof ArtistContains)) return false;
			final ArtistContains that = (ArtistContains) obj;
			return Objects.equals(this.lcaseSubString, that.lcaseSubString);
		}

	}

}

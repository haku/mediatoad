package com.vaguehope.dlnatoad.dlnaserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jupnp.support.contentdirectory.ContentDirectoryException;
import org.jupnp.support.model.item.AudioItem;
import org.jupnp.support.model.item.ImageItem;
import org.jupnp.support.model.item.VideoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.vaguehope.cdsc.CDSCBaseListener;
import com.vaguehope.cdsc.CDSCLexer;
import com.vaguehope.cdsc.CDSCParser;
import com.vaguehope.cdsc.CDSCParser.BinOpContext;
import com.vaguehope.cdsc.CDSCParser.LogOpContext;
import com.vaguehope.cdsc.CDSCParser.RelExpContext;
import com.vaguehope.cdsc.CDSCParser.SearchExpContext;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.Sqlite;
import com.vaguehope.dlnatoad.media.ContentGroup;
import com.vaguehope.dlnatoad.media.ContentItem;
import com.vaguehope.dlnatoad.media.ContentNode;
import com.vaguehope.dlnatoad.media.MetadataReader.Metadata;
import com.vaguehope.dlnatoad.util.StringHelper;

public class SearchEngine {

	private static final Logger LOG = LoggerFactory.getLogger(SearchEngine.class);

	public SearchEngine () {}

	public List<ContentItem> search (final ContentNode node, final String searchCriteria, final int maxResults, final String username) throws ContentDirectoryException {
		final long startTime = System.nanoTime();
		final Predicate<ContentItem> predicate = criteriaToPredicate(searchCriteria);
		if (predicate == null) throw new ContentDirectoryException(ContentDirectoryErrorCodes.UNSUPPORTED_SEARCH_CRITERIA, "Do not know how to parse: " + searchCriteria);
		LOG.debug("'{}' => {} in {}ms.", searchCriteria, predicate, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));

		final List<ContentItem> results = new ArrayList<>();
		filterItems(node, predicate, maxResults, username, results);
		return results;
	}

	protected static Predicate<ContentItem> criteriaToPredicate (final String searchCriteria) {
		final CriteriaListener listener = new CriteriaListener();
		new ParseTreeWalker().walk(listener, new CDSCParser(
				new CommonTokenStream(new CDSCLexer(CharStreams.fromString(searchCriteria)))
				).searchCrit());
		return listener.getPredicate();
	}

	private static class CriteriaListener extends CDSCBaseListener {

		private static final Set<String> TITLE_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
				"dc:title")));

		private static final Set<String> ARTIST_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
				"dc:creator", "upnp:artist")));

		private static final Set<String> TAG_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
				"tag")));

		private static final Map<String, ContentGroup> UPNP_TO_GROUP = ImmutableMap.of(
				VideoItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), ContentGroup.VIDEO,
				AudioItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), ContentGroup.AUDIO,
				ImageItem.CLASS.getValue().toLowerCase(Locale.ENGLISH), ContentGroup.IMAGE);

		private static class Exp {
			Predicate<ContentItem> left;
			LogOp logOp;
			Predicate<ContentItem> right;

			public Exp () {}

			@Override
			public String toString () {
				return String.format("{%s %s %s}", this.left, this.logOp, this.right);
			}
		}

		private final LinkedList<Exp> stack = new LinkedList<>();
		private Exp cExp = new Exp();

		public CriteriaListener () {}

		public Predicate<ContentItem> getPredicate () {
			if (this.stack.size() > 0) throw new IllegalStateException("Items left over on stack: " + this.stack);
			if (this.cExp.left == null) throw new IllegalStateException("Left is null: " + this.cExp);
			if (this.cExp.logOp != null) throw new IllegalStateException("Op not null: " + this.cExp);
			if (this.cExp.right != null) throw new IllegalStateException("Right not null: " + this.cExp);
			return this.cExp.left;
		}

		private static Predicate<ContentItem> collapse (final Exp exp) {
			if (exp.left == null) throw new IllegalStateException("Left is null: " + exp);
			if (exp.logOp == null) throw new IllegalStateException("Op is null: " + exp);
			if (exp.right == null) throw new IllegalStateException("Right is null: " + exp);

			switch (exp.logOp) {
				case AND:
					return new And<>(Arrays.asList(exp.left, exp.right));
				case OR:
					return new Or<>(Arrays.asList(exp.left, exp.right));
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

			if ("or".equalsIgnoreCase(ctx.getText())) {
				this.cExp.logOp = LogOp.OR;
			}
			else if ("and".equalsIgnoreCase(ctx.getText())) {
				this.cExp.logOp = LogOp.AND;
			}
			else {
				throw new IllegalArgumentException("Unknown LogOp: " + ctx.getText());
			}
		}

		@Override
		public void exitRelExp (final RelExpContext ctx) {
			final BinOpContext binOp = ctx.binOp();
			final TerminalNode quotedVal = ctx.QuotedVal();

			final String propertyName = ctx.Property().getText();
			final String op = binOp != null ? binOp.getText() : null;
			final String value = quotedVal != null ? StringHelper.unquoteQuotes(quotedVal.getText()) : null;

			final Predicate<ContentItem> predicate;

			if ("upnp:class".equals(propertyName)) {
				if ("=".equals(op) || "derivedfrom".equalsIgnoreCase(op)) {
					predicate = relExpDerivedfrom(propertyName, value);
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
					predicate = new Bool<>(true);
				}
			}
			else if (TITLE_FIELDS.contains(propertyName)) {
				if ("=".equals(op) || "contains".equalsIgnoreCase(op)) {
					predicate = new TitleContains(value);
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
					predicate = new Bool<>(true);
				}
			}
			else if (ARTIST_FIELDS.contains(propertyName)) {
				if ("=".equals(op) || "contains".equalsIgnoreCase(op)) {
					predicate = new ArtistContains(value);
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
					predicate = new Bool<>(true);
				}
			}
			else if (TAG_FIELDS.contains(propertyName)) {
				if ("=".equals(op)) {
					predicate = new TagPredicate(value, Operator.EQUAL);
				}
				else if ("!=".equals(op)) {
					predicate = new TagPredicate(value, Operator.NOT_EQUAL);
				}
				else if ("contains".equalsIgnoreCase(op)) {
					predicate = new TagPredicate(value, Operator.CONTAINS);
				}
				else if ("doesNotContain".equalsIgnoreCase(op)) {
					predicate = new TagPredicate(value, Operator.DOES_NOT_CONTAIN);
				}
				else if ("startsWith".equalsIgnoreCase(op)) {
					predicate = new TagPredicate(value, Operator.STARTS_WITH);
				}
				else {
					LOG.debug("Unsupported op for property {}: {}", propertyName, op);
					predicate = new Bool<>(true);
				}
			}
			else {
				LOG.debug("Unsupported property: {}", propertyName);
				predicate = new Bool<>(true);
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

		private static Predicate<ContentItem> relExpDerivedfrom (final String propertyName, final String value) {
			final String lcastUpnpClass = value.toLowerCase(Locale.ENGLISH);
			for (final Entry<String, ContentGroup> utc : UPNP_TO_GROUP.entrySet()) {
				if (lcastUpnpClass.startsWith(utc.getKey())) {
					return new ContentGroupIs(utc.getValue());
				}
			}
			LOG.debug("Unsupported value for property {}: {}", propertyName, value);
			return new Bool<>(true);
		}

	}

	/**
	 * Lazy recursive impl.
	 */
	private static void filterItems (final ContentNode node, final Predicate<ContentItem> predicate, final int maxResults, final String username, final List<ContentItem> results) {
		if (results.size() >= maxResults) return;
		if (!node.isUserAuth(username)) return;

		node.withEachItem(i -> {
			if (results.size() >= maxResults) return;  // TODO is there a nicer way to stop iterating?
			if (predicate.matches(i)) {
				results.add(i);
			}
		});

		// FIXME this is recursive and acquires locks so may deadlock.
		// TODO replace with non-recursive impl.
		node.withEachNode(n -> {
			if (ContentGroup.RECENT.getId().equals(n.getId())) return;  // Do not search in recent.
			filterItems(n, predicate, maxResults, username, results);
		});
	}

	private enum LogOp {
		OR, AND
	}

	private enum Operator {
		EQUAL, NOT_EQUAL, STARTS_WITH, CONTAINS, DOES_NOT_CONTAIN
	}

	protected static class Where {
		final String clause;
		final List<String> params;
		Where(final String clause, final List<String> params) {
			this.clause = clause;
			this.params = params;
		}

		public static <T> Where join(final Collection<Predicate<T>> predicates, final String seperator) {
			final StringBuilder t = new StringBuilder("(");
			final List<String> params = new ArrayList<>();
			boolean first = true;
			for (final Predicate<T> p : predicates) {
				if (!first) t.append(" ").append(seperator).append(" ");
				first = false;
				final Where w = p.getWhere();
				t.append(w.clause);
				if (w.params != null) params.addAll(w.params);
			}
			t.append(")");
			return new Where(t.toString(), params);
		}
	}

	protected interface Predicate<T> {
		boolean matches (T thing);
		Where getWhere();
	}

	private static class Bool<T> implements Predicate<T> {

		private final boolean v;

		public Bool(final boolean v) {
			this.v = v;
		}

		@Override
		public boolean matches (final T thing) {
			return this.v;
		}

		@Override
		public String toString() {
			return Boolean.toString(this.v).toUpperCase(Locale.ENGLISH);
		}

		@Override
		public Where getWhere() {
			return new Where(this.v ? "TRUE" : "FALSE", null);
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
		public Where getWhere() {
			return Where.join(this.predicates, "OR");
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
		public Where getWhere() {
			return Where.join(this.predicates, "AND");
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

	private static class ContentGroupIs implements Predicate<ContentItem> {

		private final ContentGroup contentGroup;

		public ContentGroupIs (final ContentGroup contentGroup) {
			this.contentGroup = contentGroup;
		}

		@Override
		public boolean matches (final ContentItem item) {
			return this.contentGroup == item.getFormat().getContentGroup();
		}

		@Override
		public String toString () {
			return String.format("contentGroupIs %s", this.contentGroup);
		}

		@Override
		public Where getWhere() {
			return new Bool<>(true).getWhere();  // TODO check type.
		}

		@Override
		public int hashCode () {
			return this.contentGroup.hashCode();
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (!(obj instanceof ContentGroupIs)) return false;
			final ContentGroupIs that = (ContentGroupIs) obj;
			return Objects.equals(this.contentGroup, that.contentGroup);
		}

	}

	private static class TitleContains implements Predicate<ContentItem> {

		private final String lcaseSubString;

		public TitleContains (final String subString) {
			this.lcaseSubString = subString.toLowerCase(Locale.ENGLISH);
		}

		@Override
		public boolean matches (final ContentItem item) {
			return item.getTitle().toLowerCase(Locale.ENGLISH).contains(this.lcaseSubString);
		}

		@Override
		public String toString () {
			return String.format("titleContains '%s'", this.lcaseSubString);
		}

		@Override
		public Where getWhere() {
			return new Where(MediaDb.COL_FILE + " LIKE ? ESCAPE ?",
					Arrays.asList(Sqlite.escapeSearch(this.lcaseSubString), Sqlite.SEARCH_ESC));
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

	private static class ArtistContains implements Predicate<ContentItem> {

		private final String lcaseSubString;

		public ArtistContains (final String subString) {
			this.lcaseSubString = subString.toLowerCase(Locale.ENGLISH);
		}

		@Override
		public boolean matches (final ContentItem item) {
			final Metadata md = item.getMetadata();
			if (md == null) return false;

			final String artist = md.getArtist();
			if (artist == null) return false;

			return artist.toLowerCase(Locale.ENGLISH).contains(this.lcaseSubString);
		}

		@Override
		public String toString () {
			return String.format("artistContains '%s'", this.lcaseSubString);
		}

		@Override
		public Where getWhere() {
			return new Where(MediaDb.COL_FILE + " LIKE ? ESCAPE ?",
					Arrays.asList(Sqlite.escapeSearch(this.lcaseSubString), Sqlite.SEARCH_ESC));
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

	private static class TagPredicate implements Predicate<ContentItem> {

		private final String val;
		private final Operator op;

		public TagPredicate (final String val, final Operator op) {
			this.val = val;
			this.op = op;
		}

		@Override
		public boolean matches (final ContentItem item) {
			return true;  // TODO implement for memory searches?
		}

		@Override
		public String toString () {
			return String.format("tag %s '%s'", this.op, this.val);
		}

		@Override
		public Where getWhere() {
			final String q;
			final String v;
			switch (this.op) {
				case EQUAL:
					q = " = ?";
					v = this.val;
					break;
				case NOT_EQUAL:
					q = " != ?";
					v = this.val;
					break;
				case STARTS_WITH:
					q = " LIKE ? ESCAPE ?";
					v = Sqlite.escapeSearch(this.val) + "%";
					break;
				case CONTAINS:
					q = " LIKE ? ESCAPE ?";
					v = "%" + Sqlite.escapeSearch(this.val) + "%";
					break;
				case DOES_NOT_CONTAIN:
					q = " NOT LIKE ? ESCAPE ?";
					v = "%" + Sqlite.escapeSearch(this.val) + "%";
					break;
					default:
						throw new IllegalArgumentException("Unsupported Operator: " + this.op);
			}
			return new Where(MediaDb.COL_TAG + q, Arrays.asList(v, Sqlite.SEARCH_ESC));
		}

		@Override
		public int hashCode () {
			return Objects.hash(this.val, this.op);
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (!(obj instanceof TagPredicate)) return false;
			final TagPredicate that = (TagPredicate) obj;
			return Objects.equals(this.val, that.val)
					&& Objects.equals(this.op, that.op);
		}

	}

}

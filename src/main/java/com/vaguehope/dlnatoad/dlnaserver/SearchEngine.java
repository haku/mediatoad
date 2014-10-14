package com.vaguehope.dlnatoad.dlnaserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.support.contentdirectory.ContentDirectoryException;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

import com.vaguehope.cdsc.CDSCBaseListener;
import com.vaguehope.cdsc.CDSCLexer;
import com.vaguehope.cdsc.CDSCParser;
import com.vaguehope.cdsc.CDSCParser.RelExpContext;
import com.vaguehope.dlnatoad.util.StringHelper;

public class SearchEngine {

	private static final Logger LOG = LoggerFactory.getLogger(SearchEngine.class);

	public SearchEngine () {}

	public List<Item> search (final ContentNode contentNode, final String searchCriteria) throws ContentDirectoryException {
		final CriteriaListener listener = new CriteriaListener();
		new ParseTreeWalker().walk(listener, new CDSCParser(
				new CommonTokenStream(new CDSCLexer(new ANTLRInputStream(searchCriteria)))
				).searchCrit());

		// FIXME Force parse a title query ignoring everything else.
		final String term = listener.getTitle();
		if (term == null || term.length() < 1) throw new ContentDirectoryException(ContentDirectoryErrorCodes.UNSUPPORTED_SEARCH_CRITERIA, "Do not know how to parse: " + searchCriteria);
		return filterByTitleSubstring(contentNode.getContainer(), term.toLowerCase());
	}

	private static class CriteriaListener extends CDSCBaseListener {

		private String title;

		public CriteriaListener () {}

		public String getTitle () {
			return this.title;
		}

		@Override
		public void enterRelExp (@NotNull final RelExpContext ctx) {
			final String propertyName = ctx.Property().getText();
			if ("dc:title".equals(propertyName)) {
				final String op = ctx.binOp().getText();
				if ("contains".equals(op)) {
					if (this.title == null) {
						this.title = StringHelper.unquoteQuotes(ctx.QuotedVal().getText());
					}
					else {
						LOG.warn("Ignoring additional title paramter: {}", ctx.QuotedVal());
					}
				}
				else {
					LOG.warn("Unknown title op: {}", op);
				}
			}
		}

	}

	/**
	 * Lazy recursive impl.
	 */
	private static List<Item> filterByTitleSubstring (final Container container, final String lcaseTerm) {
		final List<Item> results = new ArrayList<>();
		for (final Item ci : container.getItems()) {
			if (ci.getTitle().toLowerCase(Locale.ENGLISH).contains(lcaseTerm)) results.add(ci);
		}
		if (container.getContainers() != null) {
			for (final Container childContainer : container.getContainers()) {
				results.addAll(filterByTitleSubstring(childContainer, lcaseTerm));
			}
		}
		return results;
	}

}

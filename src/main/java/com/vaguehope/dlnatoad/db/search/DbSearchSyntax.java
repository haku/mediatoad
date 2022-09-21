package com.vaguehope.dlnatoad.db.search;

import org.apache.commons.lang3.StringUtils;

public class DbSearchSyntax {

	public static String makeSingleTagSearch(String tag) {
		final String quote;
		if (StringUtils.containsAny(tag, ' ', '(', ')', '\t', '　')) {
			if (tag.indexOf('"') >= 0) {
				if (tag.indexOf('\'') >= 0) {
					tag = tag.replace("'", "\\'");
				}
				quote = "'";
			}
			else {
				quote = "\"";
			}
		}
		else {
			quote = "";
		}
		final StringBuilder ret = new StringBuilder();
		ret.append("t=");
		ret.append(quote);
		ret.append(tag);
		ret.append(quote);
		return ret.toString();
	}

}

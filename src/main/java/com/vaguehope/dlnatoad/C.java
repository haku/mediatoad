package com.vaguehope.dlnatoad;

import java.util.regex.Pattern;

public interface C {

	String APPNAME = "DLNAtoad";

	String METADATA_MANUFACTURER = "VagueHope";

	String METADATA_MODEL_NAME = "DLNAtoad";
	String METADATA_MODEL_DESCRIPTION = "DLNAtoad MediaServer";
	String METADATA_MODEL_NUMBER = "v1";

	int HTTP_PORT = 8192;

	/**
	 * Shorter version of org.teleal.cling.model.Constants.MIN_ADVERTISEMENT_AGE_SECONDS.
	 * Remove when Cling 2.0 has a stable release.
	 * http://4thline.org/projects/mailinglists.html#nabble-td2183974
	 * http://4thline.org/projects/mailinglists.html#nabble-td2183974
	 * https://github.com/4thline/cling/issues/41
	 */
	int MIN_ADVERTISEMENT_AGE_SECONDS = 300;

	String REVERSE_PROXY_PATH = "dlnatoad";
	String CONTENT_PATH_PREFIX = "c/";
	String AUTOCOMPLETE_PATH = "ac";
	String ITEM_PATH_PREFIX = "i/";
	String STATIC_FILES_PATH_PREFIX = "w/";
	String THUMBS_PATH_PREFIX = "t/";

	long DEVICE_SEARCH_INTERVAL_MINUTES = 15;

	Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

}

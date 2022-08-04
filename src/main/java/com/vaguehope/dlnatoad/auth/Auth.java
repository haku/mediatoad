package com.vaguehope.dlnatoad.auth;

import java.util.concurrent.TimeUnit;

public final class Auth {

	public static final String TOKEN_COOKIE_NAME = "DLNATOADTOKEN";
	public static final long MAX_TOKEN_AGE_MILLIS = TimeUnit.DAYS.toMillis(30);

}

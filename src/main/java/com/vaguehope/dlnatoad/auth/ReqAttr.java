package com.vaguehope.dlnatoad.auth;

import javax.servlet.http.HttpServletRequest;

public class ReqAttr<T> {

	public static final ReqAttr<String> USERNAME = new ReqAttr<>("dlnatoad.username", String.class);

	private final String attrName;
	private final Class<T> cls;

	public ReqAttr(final String attrName, final Class<T> cls) {
		this.attrName = attrName;
		this.cls = cls;
	}

	public void set(final HttpServletRequest req, T val) {
		req.setAttribute(this.attrName, val);
	}

	public T get(final HttpServletRequest req) {
		return this.cls.cast(req.getAttribute(this.attrName));
	}

}

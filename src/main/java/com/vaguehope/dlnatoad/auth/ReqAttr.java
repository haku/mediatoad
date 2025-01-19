package com.vaguehope.dlnatoad.auth;

import javax.servlet.http.HttpServletRequest;

public class ReqAttr<T> {

	public static final ReqAttr<String> USERNAME = new ReqAttr<>("mediatoad.username", String.class);
	public static final ReqAttr<Boolean> ALLOW_REMOTE_SEARCH = new ReqAttr<>("mediatoad.remotesearch", Boolean.class, Boolean.FALSE);
	public static final ReqAttr<Boolean> ALLOW_UPNP_INSPECTOR = new ReqAttr<>("mediatoad.upnpinspector", Boolean.class, Boolean.FALSE);
	public static final ReqAttr<Boolean> ALLOW_EDIT_TAGS = new ReqAttr<>("mediatoad.edittags", Boolean.class, Boolean.FALSE);
	public static final ReqAttr<Boolean> ALLOW_EDIT_DIR_PREFS = new ReqAttr<>("mediatoad.editdirprefs", Boolean.class, Boolean.FALSE);
	public static final ReqAttr<Boolean> ALLOW_MANAGE_RPC = new ReqAttr<>("mediatoad.managerpc", Boolean.class, Boolean.FALSE);

	private final String attrName;
	private final Class<T> cls;
	private final T defVal;

	public ReqAttr(final String attrName, final Class<T> cls) {
		this(attrName, cls, null);
	}

	public ReqAttr(final String attrName, final Class<T> cls, final T defVal) {
		this.attrName = attrName;
		this.cls = cls;
		this.defVal = defVal;
	}

	public void set(final HttpServletRequest req, final T val) {
		req.setAttribute(this.attrName, val);
	}

	public T get(final HttpServletRequest req) {
		final Object attr = req.getAttribute(this.attrName);
		if (attr == null) return this.defVal;
		return this.cls.cast(attr);
	}

}

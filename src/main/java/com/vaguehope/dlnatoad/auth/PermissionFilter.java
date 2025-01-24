package com.vaguehope.dlnatoad.auth;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import com.vaguehope.dlnatoad.ui.ServletCommon;

public class PermissionFilter implements Filter {

	private final ReqAttr<Boolean> reqAttr;

	public PermissionFilter(final ReqAttr<Boolean> reqAttr) {
		this.reqAttr = reqAttr;
	}

	@Override
	public void doFilter(final ServletRequest req, final ServletResponse resp, final FilterChain chain) throws IOException, ServletException {
		if (!Boolean.TRUE.equals(this.reqAttr.get(req))) {
			ServletCommon.returnForbidden((HttpServletResponse) resp);
			return;
		}

		chain.doFilter(req, resp);
	}

}

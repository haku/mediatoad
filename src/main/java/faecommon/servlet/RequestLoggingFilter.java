package faecommon.servlet;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import faecommon.util.StringsCommon;

public class RequestLoggingFilter implements Filter {

	private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

	@Override
	public void init(final FilterConfig filterConfig) throws ServletException {

	}

	@SuppressFBWarnings("CRLF_INJECTION_LOGS")
	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse resp = (HttpServletResponse) response;

		final String remoteAddr = StringsCommon.firstLineMaxLength(req.getRemoteAddr(), 50);
		final String requestURI = StringsCommon.firstLineMaxLength(req.getRequestURI(), 50);
		final String method = StringsCommon.firstLineMaxLength(req.getMethod(), 10);
		final String ranges = joinEnumeration(req.getHeaders("Range"), ",");

		try {
			chain.doFilter(request, response);
		}
		finally {
			if (ranges != null) {
				LOG.info("{} {} {} (r:{}) {}", resp.getStatus(), method, requestURI, ranges, remoteAddr);
			}
			else {
				LOG.info("{} {} {} {}", resp.getStatus(), method, requestURI, remoteAddr);
			}
		}
	}

	private static String joinEnumeration(final Enumeration<String> en, final String join) {
		if (en == null || !en.hasMoreElements()) return null;
		StringBuilder s = new StringBuilder(StringsCommon.firstLineMaxLength(en.nextElement(), 50));
		while (en.hasMoreElements()) {
			s.append(join).append(StringsCommon.firstLineMaxLength(en.nextElement(), 50));
		}
		return s.toString();
	}

	@Override
	public void destroy() {

	}

	public static void addTo(ServletContextHandler handler) {
		handler.addFilter(new FilterHolder(new RequestLoggingFilter()), "/*", null);
	}

}

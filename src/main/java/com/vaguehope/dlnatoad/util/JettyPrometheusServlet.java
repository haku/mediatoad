package com.vaguehope.dlnatoad.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.exporter.common.PrometheusHttpExchange;
import io.prometheus.metrics.exporter.common.PrometheusHttpRequest;
import io.prometheus.metrics.exporter.common.PrometheusHttpResponse;
import io.prometheus.metrics.exporter.common.PrometheusScrapeHandler;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

/**
 * like io.prometheus.metrics.exporter.servlet.jakarta but for javax.servlet.http.HttpServlet instead of jakarta
 */
public class JettyPrometheusServlet extends HttpServlet {

	private static final long serialVersionUID = -9217004546768226256L;
	private final PrometheusScrapeHandler handler;

	public JettyPrometheusServlet() {
		this.handler = new PrometheusScrapeHandler(PrometheusProperties.get(), PrometheusRegistry.defaultRegistry);
	}

	@SuppressWarnings("resource")
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		this.handler.handleRequest(new HttpExchangeAdapter(request, response));
	}

	/**
	 * based on io.prometheus.metrics.exporter.servlet.jakarta.HttpExchangeAdapter
	 */
	private static class HttpExchangeAdapter implements PrometheusHttpExchange {

		private final Request request;
		private final Response response;

		public HttpExchangeAdapter(HttpServletRequest request, HttpServletResponse response) {
			this.request = new Request(request);
			this.response = new Response(response);
		}

		@Override
		public PrometheusHttpRequest getRequest() {
			return this.request;
		}

		@Override
		public PrometheusHttpResponse getResponse() {
			return this.response;
		}

		@Override
		public void handleException(IOException e) throws IOException {
			throw e; // leave exception handling to the servlet container
		}

		@Override
		public void handleException(RuntimeException e) {
			throw e; // leave exception handling to the servlet container
		}

		@Override
		public void close() {
			// nothing to do for Servlets.
		}

		public static class Request implements PrometheusHttpRequest {

			private final HttpServletRequest request;

			public Request(HttpServletRequest request) {
				this.request = request;
			}

			@Override
			public String getQueryString() {
				return this.request.getQueryString();
			}

			@Override
			public Enumeration<String> getHeaders(String name) {
				return this.request.getHeaders(name);
			}

			@Override
			public String getMethod() {
				return this.request.getMethod();
			}

			@Override
			public String getRequestPath() {
				StringBuilder uri = new StringBuilder();
				String contextPath = this.request.getContextPath();
				if (contextPath.startsWith("/")) {
					uri.append(contextPath);
				}
				String servletPath = this.request.getServletPath();
				if (servletPath.startsWith("/")) {
					uri.append(servletPath);
				}
				String pathInfo = this.request.getPathInfo();
				if (pathInfo != null) {
					uri.append(pathInfo);
				}
				return uri.toString();
			}
		}

		public static class Response implements PrometheusHttpResponse {

			private final HttpServletResponse response;

			public Response(HttpServletResponse response) {
				this.response = response;
			}

			@Override
			public void setHeader(String name, String value) {
				this.response.setHeader(name, value);
			}

			@Override
			public OutputStream sendHeadersAndGetBody(int statusCode, int contentLength) throws IOException {
				if (this.response.getHeader("Content-Length") == null && contentLength > 0) {
					this.response.setContentLength(contentLength);
				}
				this.response.setStatus(statusCode);
				return this.response.getOutputStream();
			}
		}
	}

}

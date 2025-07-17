package com.vaguehope.dlnatoad.httpserver;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewritePatternRule;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jupnp.UpnpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.common.rpc.RpcPrometheusMetrics;
import com.vaguehope.common.rpc.RpcStatusServlet;
import com.vaguehope.common.servlet.RequestLoggingFilter;
import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.Args.ArgsException;
import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.auth.AuthFilter;
import com.vaguehope.dlnatoad.auth.AuthTokens;
import com.vaguehope.dlnatoad.auth.PermissionFilter;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.auth.Users;
import com.vaguehope.dlnatoad.db.DbCache;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.TagAutocompleter;
import com.vaguehope.dlnatoad.media.ContentServingHistory;
import com.vaguehope.dlnatoad.media.ContentServlet;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.ThumbnailGenerator;
import com.vaguehope.dlnatoad.rpc.client.RemoteContentServlet;
import com.vaguehope.dlnatoad.rpc.client.RpcClient;
import com.vaguehope.dlnatoad.rpc.server.JwkLoader;
import com.vaguehope.dlnatoad.rpc.server.JwtInterceptor;
import com.vaguehope.dlnatoad.rpc.server.MediaImpl;
import com.vaguehope.dlnatoad.rpc.server.RpcAuthServlet;
import com.vaguehope.dlnatoad.rpc.server.RpcDivertingHandler;
import com.vaguehope.dlnatoad.rpc.server.RpcServlet;
import com.vaguehope.dlnatoad.ui.AutocompleteServlet;
import com.vaguehope.dlnatoad.ui.DirServlet;
import com.vaguehope.dlnatoad.ui.IndexServlet;
import com.vaguehope.dlnatoad.ui.ItemServlet;
import com.vaguehope.dlnatoad.ui.SearchServlet;
import com.vaguehope.dlnatoad.ui.ServletCommon;
import com.vaguehope.dlnatoad.ui.StaticFilesServlet;
import com.vaguehope.dlnatoad.ui.TagsServlet;
import com.vaguehope.dlnatoad.ui.ThumbsServlet;
import com.vaguehope.dlnatoad.ui.UpnpServlet;
import com.vaguehope.dlnatoad.ui.WebdavDivertingHandler;
import com.vaguehope.dlnatoad.ui.WebdavServlet;
import com.vaguehope.dlnatoad.util.JettyPrometheusServlet;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;

public class HttpServer {

	private static final Logger LOG = LoggerFactory.getLogger(HttpServer.class);

	private final ContentTree contentTree;
	private final MediaDb mediaDb;
	private final DbCache dbCache;
	private final TagAutocompleter tagAutocompleter;
	private final UpnpService upnpService;
	private final RpcClient rpcClient;
	private final ThumbnailGenerator thumbnailGenerator;
	private final Args args;
	private final List<InetAddress> bindAddresses;
	private final String hostName;

	public HttpServer(
			final ContentTree contentTree,
			final MediaDb mediaDb,
			final DbCache dbCache,
			final TagAutocompleter tagAutocompleter,
			final UpnpService upnpService,
			final RpcClient rpcClient,
			final ThumbnailGenerator thumbnailGenerator,
			final Args args,
			final List<InetAddress> bindAddresses,
			final String hostName) {
		this.contentTree = contentTree;
		this.mediaDb = mediaDb;
		this.dbCache = dbCache;
		this.tagAutocompleter = tagAutocompleter;
		this.upnpService = upnpService;
		this.rpcClient = rpcClient;
		this.thumbnailGenerator = thumbnailGenerator;
		this.args = args;
		this.bindAddresses = bindAddresses;
		this.hostName = hostName;
	}

	public Server start() throws Exception {
		final Server server = makeServer();

		GaugeWithCallback.builder().name("jetty_all_threads")
				.callback((cb) -> cb.call(server.getThreadPool().getThreads()))
				.register();
		GaugeWithCallback.builder().name("jetty_idle_threads")
				.callback((cb) -> cb.call(server.getThreadPool().getIdleThreads()))
				.register();

		return server;
	}

	private Server makeServer() throws Exception {
		int port = this.args.getPort();
		final boolean defaultPort;
		if (port < 1) {
			port = C.HTTP_PORT;
			defaultPort = true;
		}
		else {
			defaultPort = false;
		}

		final File userfile = this.args.getUserfile();
		final Users users = userfile != null ? new Users(userfile) : null;
		final File rpcAuthFile = this.args.getRpcAuthFile();
		final JwkLoader rpcJwtLoader = rpcAuthFile != null ? new JwkLoader(rpcAuthFile) : null;

		while (true) {
			final Handler rpcHandler = makeRpcHandler(users, rpcJwtLoader);
			final Handler mainHandler = makeContentHandler(users, rpcJwtLoader);
			final Handler handler = new RpcDivertingHandler(rpcHandler, mainHandler);

			final Server server = new Server();
			server.setHandler(maybeWrapWithRewrites(handler));

			if (this.bindAddresses != null) {
				for (final InetAddress address : this.bindAddresses) {
					server.addConnector(createHttpConnector(server, address, port));
				}
			}
			else {
				server.addConnector(createHttpConnector(server, null, port));
			}

			try {
				server.start();
				return server;
			}
			catch (final BindException e) {
				if (!defaultPort) throw e;
				if ("Address already in use".equals(e.getMessage())) {
					LOG.info("Retrying with higher port...");
					port += 1;
				}
				else {
					throw e;
				}
			}
		}
	}

	private Handler makeContentHandler(final Users users, final JwkLoader rpcJwtLoader) throws ArgsException, IOException {
		final ServletContextHandler servletHandler = new ServletContextHandler();
		MediaFormat.addTo(servletHandler.getMimeTypes());
		servletHandler.setContextPath("/");
		if (this.args.isVerboseLog()) {
			servletHandler.setErrorHandler(new ErrorHandler());
		}

		// Lets start really small and increase later if needed.
		servletHandler.setMaxFormContentSize(1024);
		servletHandler.setMaxFormKeys(10);

		if (this.args.isPrintAccessLog()) {
			RequestLoggingFilter.addTo(servletHandler);
		}

		final AuthTokens authTokens = new AuthTokens(this.args.getSessionDir());
		if (this.args.isOpenIdFlagSet()) {
			new OpenId(this.args, users).addToHandler(servletHandler);
		}
		final AuthFilter authFilter = new AuthFilter(users, authTokens, this.args.getHttpPathPrefix(), this.args.isPrintAccessLog());
		servletHandler.addFilter(new FilterHolder(authFilter), "/*", null);

		final ContentServingHistory contentServingHistory = new ContentServingHistory();
		final ServletCommon servletCommon = new ServletCommon(this.contentTree, this.hostName, contentServingHistory, this.mediaDb != null, this.args);

		servletHandler.addServlet(new ServletHolder(new UpnpServlet(this.upnpService)), "/upnp");
		if (rpcJwtLoader != null) {
			servletHandler.addServlet(new ServletHolder(new RpcAuthServlet(rpcJwtLoader)), RpcAuthServlet.CONTEXTPATH);
			servletHandler.addFilter(new FilterHolder(new PermissionFilter(ReqAttr.ALLOW_MANAGE_RPC)), RpcStatusServlet.CONTEXTPATH + "/*", null);
			servletHandler.addServlet(new ServletHolder(new RpcStatusServlet()), RpcStatusServlet.CONTEXTPATH);
		}
		servletHandler.addServlet(new ServletHolder(new RemoteContentServlet(this.rpcClient, servletCommon)), "/" + C.REMOTE_CONTENT_PATH_PREFIX + "*");

		final ContentServlet contentServlet = new ContentServlet(this.contentTree, contentServingHistory, servletCommon);
		servletHandler.addServlet(new ServletHolder(contentServlet), "/" + C.CONTENT_PATH_PREFIX + "*");

		final DirServlet dirServlet = new DirServlet(servletCommon, this.contentTree, this.thumbnailGenerator, this.mediaDb, this.dbCache);
		servletHandler.addServlet(new ServletHolder(dirServlet), "/" + C.DIR_PATH_PREFIX + "*");

		servletHandler.addServlet(new ServletHolder(new SearchServlet(servletCommon, this.contentTree, contentServlet, this.mediaDb, this.dbCache, this.upnpService, this.rpcClient, this.thumbnailGenerator)), "/" + C.SEARCH_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new ThumbsServlet(this.contentTree, this.thumbnailGenerator, servletCommon)), "/" + C.THUMBS_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new AutocompleteServlet(this.tagAutocompleter)), "/" + C.AUTOCOMPLETE_PATH);
		servletHandler.addServlet(new ServletHolder(new ItemServlet(servletCommon, this.contentTree, this.mediaDb, this.tagAutocompleter)), "/" + C.ITEM_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new TagsServlet(this.contentTree, this.mediaDb, this.tagAutocompleter)), "/" + C.TAGS_PATH);
		servletHandler.addServlet(new ServletHolder(new StaticFilesServlet(this.args.getWebRoot())), "/" + C.STATIC_FILES_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new IndexServlet(this.contentTree, contentServlet, dirServlet, servletCommon)), "/*");

		servletHandler.addServlet(new ServletHolder(new JettyPrometheusServlet()), "/metrics");

		final ServletContextHandler webavHandler = makeWebdavHandler(authFilter, this.contentTree, this.mediaDb, servletCommon, this.args);

		return new WebdavDivertingHandler(webavHandler, servletHandler);
	}

	private static ServletContextHandler makeWebdavHandler(
			final AuthFilter authFilter,
			final ContentTree contentTree,
			final MediaDb mediaDb,
			final ServletCommon servletCommon,
			final Args args) {
		final ServletContextHandler handler = new ServletContextHandler();
		handler.setContextPath("/");

		if (args.isPrintAccessLog()) {
			RequestLoggingFilter.addTo(handler);
		}

		handler.addFilter(new FilterHolder(authFilter), "/*", null);
		handler.addServlet(new ServletHolder(new WebdavServlet(contentTree, mediaDb, servletCommon)), "/*");
		return handler;
	}

	private Handler makeRpcHandler(final Users users, final JwkLoader rpcJwtLoader) throws IOException {
		if (rpcJwtLoader == null) return null;
		RpcPrometheusMetrics.setup();

		final ServletContextHandler handler = new ServletContextHandler();
		handler.setContextPath("/");

		if (this.args.isPrintAccessLog()) {
			RequestLoggingFilter.addTo(handler);
		}

		final JwtInterceptor jwtInterceptor = new JwtInterceptor(rpcJwtLoader, users);
		final MediaImpl mediaImpl = new MediaImpl(this.contentTree, this.mediaDb);
		handler.addServlet(new ServletHolder(new RpcServlet(jwtInterceptor, mediaImpl)), "/*");
		return handler;
	}

	protected Handler maybeWrapWithRewrites(final Handler wrapped) throws ArgsException {
		final String prefix = this.args.getHttpPathPrefix();
		if (prefix == null) return wrapped;

		final RewriteHandler rewrites = new RewriteHandler();
		// Do not modify the request object because:
		// - RuleContainer.apply() messes up the encoding.
		// - ServletHelper.getReqPath() knows how to remove the prefix.
		rewrites.setRewriteRequestURI(false);
		rewrites.setRewritePathInfo(false);
		rewrites.addRule(new RewritePatternRule("/" + prefix + "/*", "/"));
		rewrites.setHandler(wrapped);
		return rewrites;
	}

	private Connector createHttpConnector(final Server server, final InetAddress bindAddress, final int port) {
		ForwardedRequestCustomizer forwardedCustomizer = null;
		if (this.args.isTrustForwardedHeader()) {
			forwardedCustomizer = new ForwardedRequestCustomizer();
			forwardedCustomizer.setForwardedOnly(true);
		}

		final HttpConfiguration http1Config = new HttpConfiguration();
		if (forwardedCustomizer != null) http1Config.addCustomizer(forwardedCustomizer);
		final HttpConnectionFactory http1 = new HttpConnectionFactory(http1Config);

		final HttpConfiguration http2Config = new HttpConfiguration(http1Config);
		// increase from 30s default in org.eclipse.jetty.server.AbstractConnector._idleTimeout.
		// work around for issue where kodi gets upset when the connection times out and complains:
		// "Stream error in the HTTP/2 framing layer".
		// since http2 is async, long timeouts should have minimal overhead?
		http2Config.setIdleTimeout(TimeUnit.SECONDS.toMillis(300));
		final HTTP2CServerConnectionFactory http2 = new HTTP2CServerConnectionFactory(http2Config);

		final ServerConnector connector = new ServerConnector(server, http1, http2);
		if (bindAddress != null) connector.setHost(bindAddress.getHostName());
		connector.setPort(port);
		return connector;
	}

}

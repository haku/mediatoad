package com.vaguehope.dlnatoad;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewritePatternRule;
import org.eclipse.jetty.server.Connector;
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
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.akuma.Daemon;
import com.vaguehope.common.rpc.RpcPrometheusMetrics;
import com.vaguehope.common.rpc.RpcStatusServlet;
import com.vaguehope.common.servlet.RequestLoggingFilter;
import com.vaguehope.dlnatoad.Args.ArgsException;
import com.vaguehope.dlnatoad.auth.AuthFilter;
import com.vaguehope.dlnatoad.auth.AuthTokens;
import com.vaguehope.dlnatoad.auth.Users;
import com.vaguehope.dlnatoad.auth.UsersCli;
import com.vaguehope.dlnatoad.db.DbCache;
import com.vaguehope.dlnatoad.db.DbCleaner;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.db.MediaMetadataStore;
import com.vaguehope.dlnatoad.db.TagAutocompleter;
import com.vaguehope.dlnatoad.dlnaserver.DlnaService;
import com.vaguehope.dlnatoad.dlnaserver.MediaServer;
import com.vaguehope.dlnatoad.dlnaserver.NodeConverter;
import com.vaguehope.dlnatoad.dlnaserver.SystemId;
import com.vaguehope.dlnatoad.importer.MetadataImporter;
import com.vaguehope.dlnatoad.media.ContentServingHistory;
import com.vaguehope.dlnatoad.media.ContentServlet;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.ExternalUrls;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MediaId;
import com.vaguehope.dlnatoad.media.MediaIndex;
import com.vaguehope.dlnatoad.media.MediaIndex.HierarchyMode;
import com.vaguehope.dlnatoad.media.MediaInfo;
import com.vaguehope.dlnatoad.media.ThumbnailGenerator;
import com.vaguehope.dlnatoad.rpc.client.RemoteContentServlet;
import com.vaguehope.dlnatoad.rpc.client.RpcClient;
import com.vaguehope.dlnatoad.rpc.server.JwtInterceptor;
import com.vaguehope.dlnatoad.rpc.server.JwtLoader;
import com.vaguehope.dlnatoad.rpc.server.MediaImpl;
import com.vaguehope.dlnatoad.rpc.server.RpcAuthServlet;
import com.vaguehope.dlnatoad.rpc.server.RpcDivertingHandler;
import com.vaguehope.dlnatoad.rpc.server.RpcServlet;
import com.vaguehope.dlnatoad.tagdeterminer.TagDeterminerController;
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
import com.vaguehope.dlnatoad.util.ExecutorHelper;
import com.vaguehope.dlnatoad.util.JettyPrometheusServlet;
import com.vaguehope.dlnatoad.util.LogHelper;
import com.vaguehope.dlnatoad.util.NetHelper;
import com.vaguehope.dlnatoad.util.ProgressLogFileListener;
import com.vaguehope.dlnatoad.util.Watcher;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.instrumentation.jvm.JvmMemoryMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public final class Main {

	protected static final Logger LOG = LoggerFactory.getLogger(Main.class);

	private Main () {
		throw new AssertionError();
	}

	public static void main (final String[] rawArgs) throws Exception { // NOSONAR
		LogHelper.bridgeJul();

		final PrintStream err = System.err;
		final Args args = new Args();
		final CmdLineParser parser = new CmdLineParser(args);
		try {
			parser.parseArgument(rawArgs);
			if (args.isHelp()) {
				help(parser, System.out);
				return;
			}
			daemonise(args);
			run(args);
		}
		catch (final ArgsException e) {
			err.println(e.getMessage());
			help(parser, err);
			return;
		}
		catch (final Exception e) {
			err.println("An unhandled error occured.");
			e.printStackTrace(err);
			System.exit(1);
		}
	}

	private static void daemonise (final Args args) throws Exception {
		final Daemon d = new Daemon.WithoutChdir();
		if (d.isDaemonized()) {
			d.init(null); // No PID file for now.
		}
		else if (args.isDaemonise()) {
			d.daemonize();
			LOG.info("Daemon started.");
			System.exit(0);
		}
	}

	private static void run (final Args args) throws Exception { // NOSONAR
		if (args.isAddUser()) {
			final int status = UsersCli.interactivlyAddUser(args);
			if (status != 0) System.exit(status);
			return;
		}

		JvmMemoryMetrics.builder().register();

		final String hostName = InetAddress.getLocalHost().getHostName();
		LOG.info("hostName: {}", hostName);

		final List<InetAddress> bindAddresses = args.getInterfaces();
		final InetAddress selfAddress = NetHelper.guessSelfAddress(bindAddresses);

		// For DB operations, ideally no other long running work should be in this pool.
		final ScheduledExecutorService dbEx = ExecutorHelper.newScheduledExecutor(1, "db");

		// Only for hashing files.
		final ExecutorService fsEx = ExecutorHelper.newExecutor(1, "fs");

		final File thumbsDir = args.getThumbsDir();
		final ThumbnailGenerator thumbnailGenerator =
				thumbsDir != null
				? new ThumbnailGenerator(thumbsDir)
				: null;

		final File dbFile = args.getDb();
		final MediaDb mediaDb;
		final DbCache dbCache;
		final MediaMetadataStore mediaMetadataStore;
		final TagAutocompleter tagAutocompleter;
		if (dbFile != null) {
			LOG.info("DB: {}", dbFile.getAbsolutePath());
			mediaDb = new MediaDb(dbFile);
			dbCache = new DbCache(mediaDb, dbEx, args.isVerboseLog());
			mediaMetadataStore = new MediaMetadataStore(mediaDb, dbEx, fsEx, args.isVerboseLog());
			mediaMetadataStore.registerMetrics(PrometheusRegistry.defaultRegistry);
			tagAutocompleter = new TagAutocompleter(mediaDb, dbEx);
		}
		else {
			mediaDb = null;
			dbCache = null;
			mediaMetadataStore = null;
			tagAutocompleter = null;
		}

		final MediaId mediaId = new MediaId(mediaMetadataStore);
		final MediaInfo mediaInfo = new MediaInfo(mediaMetadataStore, thumbnailGenerator, ExecutorHelper.newExecutor(1, "mi"));
		final ContentTree contentTree = new ContentTree();
		contentTree.registerMetrics(PrometheusRegistry.defaultRegistry);

		final File dropDir = args.getDropDir();
		final TagDeterminerController tagDeterminerController = new TagDeterminerController(args, contentTree, mediaDb);
		final Runnable afterInitialScanIdsAllFiles = () -> {
			if (mediaDb != null) {
				new DbCleaner(contentTree, mediaDb, args.isVerboseLog()).start(dbEx);
				if (dropDir != null) {
					new MetadataImporter(dropDir, mediaDb, args.isVerboseLog()).start(dbEx);
				}
				tagAutocompleter.start();
				tagDeterminerController.start();
			}
		};

		final Runnable afterInitialScanFindsAllDirs = () -> {
			mediaId.putCallbackInQueue(afterInitialScanIdsAllFiles);
		};

		final RpcClient rpcClient = new RpcClient(args);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				rpcClient.shutdown();
			}
		});
		rpcClient.start();

		final UpnpService upnpService = new DlnaService(bindAddresses).start();
		final Server server = startContentServer(
				contentTree, mediaId, mediaDb, dbCache, tagAutocompleter, upnpService, rpcClient, thumbnailGenerator, args, bindAddresses, hostName);

		GaugeWithCallback.builder().name("jetty_all_threads")
				.callback((cb) -> cb.call(server.getThreadPool().getThreads()))
				.register();
		GaugeWithCallback.builder().name("jetty_idle_threads")
				.callback((cb) -> cb.call(server.getThreadPool().getIdleThreads()))
				.register();

		final ExternalUrls externalUrls = new ExternalUrls(selfAddress, ((ServerConnector) server.getConnectors()[0]).getPort());
		LOG.info("Self: {}", externalUrls.getSelfUri());

		final NodeConverter nodeConverter = new NodeConverter(externalUrls);

		final HierarchyMode hierarchyMode = args.isSimplifyHierarchy() ? HierarchyMode.FLATTERN : HierarchyMode.PRESERVE;
		LOG.info("hierarchyMode: {}", hierarchyMode);

		final MediaIndex index = new MediaIndex(contentTree, hierarchyMode, mediaId, mediaInfo);

		final Thread watcherThread = new Thread(new RunWatcher(args, index, afterInitialScanFindsAllDirs));
		watcherThread.setName("watcher");
		watcherThread.setDaemon(true);
		watcherThread.start();

		final SystemId systemId = new SystemId(args);
		upnpService.getRegistry().addDevice(new MediaServer(systemId, contentTree, nodeConverter, hostName, args.isPrintAccessLog(), externalUrls.getSelfUri()).getDevice());

		// Periodic rescan to catch missed devices.
		ExecutorHelper.newScheduledExecutor(1, "up").scheduleWithFixedDelay(() -> {
			upnpService.getControlPoint().search();
			if (args.isVerboseLog()) LOG.info("Scanning for devices.");
		}, 0, C.DEVICE_SEARCH_INTERVAL_MINUTES, TimeUnit.MINUTES);

		server.join(); // Keep app alive.
	}

	private static Server startContentServer(
			final ContentTree contentTree,
			final MediaId mediaId,
			final MediaDb mediaDb,
			final DbCache dbCache,
			final TagAutocompleter tagAutocompleter,
			final UpnpService upnpService,
			final RpcClient rpcClient,
			final ThumbnailGenerator thumbnailGenerator,
			final Args args,
			final List<InetAddress> bindAddresses,
			final String hostName) throws Exception {
		int port = args.getPort();
		final boolean defaultPort;
		if (port < 1) {
			port = C.HTTP_PORT;
			defaultPort = true;
		}
		else {
			defaultPort = false;
		}

		final File rpcAuthFile = args.getRpcAuthFile();
		final JwtLoader rpcJwtLoader = rpcAuthFile != null ? new JwtLoader(rpcAuthFile) : null;

		while (true) {
			final Handler rpcHandler = makeRpcHandler(rpcJwtLoader, contentTree, mediaDb, args);
			final Handler mainHandler = makeContentHandler(
					contentTree, mediaId, mediaDb, dbCache, tagAutocompleter, upnpService, rpcJwtLoader, rpcClient, thumbnailGenerator, args, hostName);
			final Handler handler = new RpcDivertingHandler(rpcHandler, mainHandler);

			final Server server = new Server();
			server.setHandler(wrapWithRewrites(handler));

			if (bindAddresses != null) {
				for (final InetAddress address : bindAddresses) {
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

	private static Handler makeContentHandler(
			final ContentTree contentTree,
			final MediaId mediaId,
			final MediaDb mediaDb,
			final DbCache dbCache,
			final TagAutocompleter tagAutocompleter,
			final UpnpService upnpService,
			final JwtLoader rpcJwtLoader,
			final RpcClient rpcClient,
			final ThumbnailGenerator thumbnailGenerator,
			final Args args,
			final String hostName) throws ArgsException, IOException {

		final ServletContextHandler servletHandler = new ServletContextHandler();
		MediaFormat.addTo(servletHandler.getMimeTypes());
		servletHandler.setContextPath("/");
		if (args.isVerboseLog()) {
			servletHandler.setErrorHandler(new ErrorHandler());
		}

		// Lets start really small and increase later if needed.
		servletHandler.setMaxFormContentSize(1024);
		servletHandler.setMaxFormKeys(10);

		if (args.isPrintAccessLog()) {
			RequestLoggingFilter.addTo(servletHandler);
		}

		final File userfile = args.getUserfile();
		final Users users = userfile != null ? new Users(userfile) : null;
		final AuthTokens authTokens = new AuthTokens(args.getSessionDir());
		final AuthFilter authFilter = new AuthFilter(users, authTokens, args.isPrintAccessLog());
		servletHandler.addFilter(new FilterHolder(authFilter), "/*", null);

		servletHandler.addServlet(new ServletHolder(new UpnpServlet(upnpService)), "/upnp");
		if (rpcJwtLoader != null) {
			servletHandler.addServlet(new ServletHolder(new RpcAuthServlet(rpcJwtLoader)), RpcAuthServlet.CONTEXTPATH);
			servletHandler.addServlet(new ServletHolder(new RpcStatusServlet()), RpcStatusServlet.CONTEXTPATH);  // TODO require permission like /upnp
		}
		servletHandler.addServlet(new ServletHolder(new RemoteContentServlet(rpcClient)), "/" + C.REMOTE_CONTENT_PATH_PREFIX + "*");

		final ContentServingHistory contentServingHistory = new ContentServingHistory();
		final ContentServlet contentServlet = new ContentServlet(contentTree, contentServingHistory);
		servletHandler.addServlet(new ServletHolder(contentServlet), "/" + C.CONTENT_PATH_PREFIX + "*");

		final ServletCommon servletCommon = new ServletCommon(contentTree, hostName, contentServingHistory, mediaDb != null, args.getTemplateRoot());

		final DirServlet dirServlet = new DirServlet(servletCommon, contentTree, thumbnailGenerator, mediaDb, dbCache);
		servletHandler.addServlet(new ServletHolder(dirServlet), "/" + C.DIR_PATH_PREFIX + "*");

		servletHandler.addServlet(new ServletHolder(new SearchServlet(servletCommon, contentTree, contentServlet, mediaDb, dbCache, upnpService, rpcClient, thumbnailGenerator)), "/" + C.SEARCH_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new ThumbsServlet(contentTree, thumbnailGenerator)), "/" + C.THUMBS_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new AutocompleteServlet(tagAutocompleter)), "/" + C.AUTOCOMPLETE_PATH);
		servletHandler.addServlet(new ServletHolder(new ItemServlet(servletCommon, contentTree, mediaDb, tagAutocompleter)), "/" + C.ITEM_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new TagsServlet(contentTree, mediaDb, tagAutocompleter)), "/" + C.TAGS_PATH);
		servletHandler.addServlet(new ServletHolder(new StaticFilesServlet(args.getWebRoot())), "/" + C.STATIC_FILES_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new IndexServlet(contentTree, contentServlet, dirServlet)), "/*");

		servletHandler.addServlet(new ServletHolder(new JettyPrometheusServlet()), "/metrics");

		final ServletContextHandler webavHandler = makeWebdavHandler(authFilter, contentTree, mediaDb, args);

		return new WebdavDivertingHandler(webavHandler, servletHandler);
	}

	private static ServletContextHandler makeWebdavHandler(final AuthFilter authFilter, final ContentTree contentTree, final MediaDb mediaDb, final Args args) {
		final ServletContextHandler handler = new ServletContextHandler();
		handler.setContextPath("/");

		if (args.isPrintAccessLog()) {
			RequestLoggingFilter.addTo(handler);
		}

		handler.addFilter(new FilterHolder(authFilter), "/*", null);
		handler.addServlet(new ServletHolder(new WebdavServlet(contentTree, mediaDb)), "/*");
		return handler;
	}

	private static Handler makeRpcHandler(final JwtLoader rpcJwtLoader, final ContentTree contentTree, final MediaDb mediaDb, final Args args) throws IOException {
		if (rpcJwtLoader == null) return null;
		RpcPrometheusMetrics.setup();

		final ServletContextHandler handler = new ServletContextHandler();
		handler.setContextPath("/");

		if (args.isPrintAccessLog()) {
			RequestLoggingFilter.addTo(handler);
		}

		final JwtInterceptor jwtInterceptor = new JwtInterceptor(rpcJwtLoader);
		final MediaImpl mediaImpl = new MediaImpl(contentTree, mediaDb);
		handler.addServlet(new ServletHolder(new RpcServlet(jwtInterceptor, mediaImpl)), "/*");
		return handler;
	}

	protected static RewriteHandler wrapWithRewrites(final Handler wrapped) {
		final RewriteHandler rewrites = new RewriteHandler();
		// Do not modify the request object because:
		// - RuleContainer.apply() messes up the encoding.
		// - ServletHelper.getReqPath() knows how to remove the prefix.
		rewrites.setRewriteRequestURI(false);
		rewrites.setRewritePathInfo(false);
		rewrites.addRule(new RewritePatternRule("/" + C.MAIN_REVERSE_PROXY_PATH + "/*", "/"));
		rewrites.addRule(new RewritePatternRule("/" + C.OLD_REVERSE_PROXY_PATH + "/*", "/"));
		rewrites.setHandler(wrapped);
		return rewrites;
	}

	private static Connector createHttpConnector(final Server server, final InetAddress bindAddress, final int port) {
		final HttpConfiguration http1Config = new HttpConfiguration();
		final HttpConnectionFactory http1 = new HttpConnectionFactory(http1Config);

		final HttpConfiguration http2Config = new HttpConfiguration();
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

	private static void help (final CmdLineParser parser, final PrintStream ps) {
		ps.print("Usage: ");
		ps.print(C.APPNAME);
		parser.printSingleLineUsage(ps);
		ps.println();
		parser.printUsage(ps);
		ps.println();
	}

	private static class RunWatcher implements Runnable {

		private final List<File> roots;
		private final boolean verboseLog;
		private final MediaIndex index;
		private final Runnable prescanCompleteListener;

		public RunWatcher (final Args args, final MediaIndex index, final Runnable prescanCompleteListener) throws ArgsException, IOException {
			this.roots = args.getDirs();  // Trigger validation in main thread.
			this.verboseLog = args.isVerboseLog();
			this.index = index;
			this.prescanCompleteListener = prescanCompleteListener;
		}

		@Override
		public void run () {
			try {
				final Watcher w = new Watcher(this.roots, MediaFormat.MediaFileFilter.INSTANCE,
						new ProgressLogFileListener(this.index, this.verboseLog));
				w.addPrescanCompleteListener(this.prescanCompleteListener);
				w.run();
				LOG.error("Watcher thread exited.");
			}
			catch (final Exception e) { // NOSONAR
				LOG.error("Watcher thread died.", e);
			}
		}

	}

}

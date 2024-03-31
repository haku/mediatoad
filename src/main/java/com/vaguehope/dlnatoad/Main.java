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
import com.vaguehope.dlnatoad.rpc.client.RemoteContentServlet;
import com.vaguehope.dlnatoad.rpc.client.RpcClient;
import com.vaguehope.dlnatoad.rpc.server.MediaImpl;
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
import com.vaguehope.dlnatoad.ui.ThumbsServlet;
import com.vaguehope.dlnatoad.ui.UpnpServlet;
import com.vaguehope.dlnatoad.util.ExecutorHelper;
import com.vaguehope.dlnatoad.util.ImageResizer;
import com.vaguehope.dlnatoad.util.LogHelper;
import com.vaguehope.dlnatoad.util.NetHelper;
import com.vaguehope.dlnatoad.util.ProgressLogFileListener;
import com.vaguehope.dlnatoad.util.RequestLoggingFilter;
import com.vaguehope.dlnatoad.util.Watcher;

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

		final String hostName = InetAddress.getLocalHost().getHostName();
		LOG.info("hostName: {}", hostName);

		final List<InetAddress> bindAddresses = args.getInterfaces();
		final InetAddress selfAddress = NetHelper.guessSelfAddress(bindAddresses);

		final ScheduledExecutorService fsExSvc = ExecutorHelper.newScheduledExecutor(1, "fs");
		final ExecutorService miExSvc = ExecutorHelper.newExecutor(1, "mi");

		final File thumbsDir = args.getThumbsDir();
		final ImageResizer imageResizer =
				thumbsDir != null
				? new ImageResizer(thumbsDir)
				: null;

		final File dbFile = args.getDb();
		final MediaDb mediaDb;
		final MediaMetadataStore mediaMetadataStore;
		final TagAutocompleter tagAutocompleter;
		if (dbFile != null) {
			LOG.info("DB: {}", dbFile.getAbsolutePath());
			mediaDb = new MediaDb(dbFile);
			mediaMetadataStore = new MediaMetadataStore(mediaDb, fsExSvc, args.isVerboseLog());
			tagAutocompleter = new TagAutocompleter(mediaDb, fsExSvc);
		}
		else {
			mediaDb = null;
			mediaMetadataStore = null;
			tagAutocompleter = null;
		}

		final MediaId mediaId = new MediaId(mediaMetadataStore);
		final MediaInfo mediaInfo = new MediaInfo(mediaMetadataStore, imageResizer, miExSvc);
		final ContentTree contentTree = new ContentTree();

		final File dropDir = args.getDropDir();
		final TagDeterminerController tagDeterminerController = new TagDeterminerController(args, contentTree, mediaDb);
		final Runnable afterInitialScanIdsAllFiles = () -> {
			if (mediaDb != null) {
				new DbCleaner(contentTree, mediaDb, args.isVerboseLog()).start(fsExSvc);
				if (dropDir != null) {
					new MetadataImporter(dropDir, mediaDb, args.isVerboseLog()).start(fsExSvc);
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
		final Server server = startContentServer(contentTree, mediaId, mediaDb, tagAutocompleter, upnpService, rpcClient, imageResizer, args, bindAddresses, hostName);

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
		final ScheduledExecutorService upnpExSvc = ExecutorHelper.newScheduledExecutor(1, "up");
		upnpExSvc.scheduleWithFixedDelay(() -> {
			upnpService.getControlPoint().search();
			if (args.isVerboseLog()) LOG.info("Scanning for devices.");
		}, 0, C.DEVICE_SEARCH_INTERVAL_MINUTES, TimeUnit.MINUTES);

		server.join(); // Keep app alive.
	}

	private static Server startContentServer(
			final ContentTree contentTree,
			final MediaId mediaId,
			final MediaDb mediaDb,
			final TagAutocompleter tagAutocompleter,
			final UpnpService upnpService,
			final RpcClient rpcClient,
			final ImageResizer imageResizer,
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

		while (true) {
			final ServletContextHandler rpcHandler = makeRpcHandler(contentTree, mediaDb, args);
			final ServletContextHandler mainHandler = makeContentHandler(contentTree, mediaId, mediaDb, tagAutocompleter, upnpService, rpcClient, imageResizer, args, hostName);
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

	private static ServletContextHandler makeContentHandler(
			final ContentTree contentTree,
			final MediaId mediaId,
			final MediaDb mediaDb,
			final TagAutocompleter tagAutocompleter,
			final UpnpService upnpService,
			final RpcClient rpcClient,
			final ImageResizer imageResizer,
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
			servletHandler.addFilter(new FilterHolder(new RequestLoggingFilter()), "/*", null);
		}

		final File userfile = args.getUserfile();
		final Users users = userfile != null ? new Users(userfile) : null;
		final AuthTokens authTokens = new AuthTokens(args.getSessionDir());
		final FilterHolder authFilterHolder = new FilterHolder(new AuthFilter(users, authTokens, contentTree, args.isPrintAccessLog()));
		servletHandler.addFilter(authFilterHolder, "/*", null);

		final ContentServingHistory contentServingHistory = new ContentServingHistory();
		final ContentServlet contentServlet = new ContentServlet(contentTree, contentServingHistory);
		servletHandler.addServlet(new ServletHolder(contentServlet), "/" + C.CONTENT_PATH_PREFIX + "*");

		servletHandler.addServlet(new ServletHolder(new RemoteContentServlet(rpcClient)), "/" + C.REMOTE_CONTENT_PATH_PREFIX + "*");

		final DbCache dbCache = mediaDb != null ? new DbCache(mediaDb) : null;
		final ServletCommon servletCommon = new ServletCommon(contentTree, hostName, contentServingHistory, mediaDb != null, args.getTemplateRoot());

		final DirServlet dirServlet = new DirServlet(servletCommon, contentTree, imageResizer, dbCache);
		servletHandler.addServlet(new ServletHolder(dirServlet), "/" + C.DIR_PATH_PREFIX + "*");

		servletHandler.addServlet(new ServletHolder(new SearchServlet(servletCommon, contentTree, mediaDb, dbCache, upnpService, rpcClient, imageResizer)), "/search");
		servletHandler.addServlet(new ServletHolder(new UpnpServlet(upnpService)), "/upnp");
		servletHandler.addServlet(new ServletHolder(new ThumbsServlet(contentTree, imageResizer)), "/" + C.THUMBS_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new AutocompleteServlet(tagAutocompleter)), "/" + C.AUTOCOMPLETE_PATH);
		servletHandler.addServlet(new ServletHolder(new ItemServlet(servletCommon, contentTree, mediaDb, tagAutocompleter)), "/" + C.ITEM_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new StaticFilesServlet(args.getWebRoot())), "/" + C.STATIC_FILES_PATH_PREFIX + "*");
		servletHandler.addServlet(new ServletHolder(new IndexServlet(contentTree, contentServlet, dirServlet)), "/*");

		return servletHandler;
	}

	private static ServletContextHandler makeRpcHandler(final ContentTree contentTree, final MediaDb mediaDb, final Args args) {
		final ServletContextHandler handler = new ServletContextHandler();
		handler.setContextPath("/");

		if (args.isPrintAccessLog()) {
			handler.addFilter(new FilterHolder(new RequestLoggingFilter()), "/*", null);
		}

		final MediaImpl mediaImpl = new MediaImpl(contentTree, mediaDb);
		handler.addServlet(new ServletHolder(new RpcServlet(mediaImpl)), "/*");
		return handler;
	}

	protected static RewriteHandler wrapWithRewrites(final Handler wrapped) {
		final RewriteHandler rewrites = new RewriteHandler();

		// Do not modify the request object because:
		// - RuleContainer.apply() messes up the encoding.
		// - ServletHelper.getReqPath() knows how to remove the prefix.
		rewrites.setRewriteRequestURI(false);
		rewrites.setRewritePathInfo(false);

		final RewritePatternRule r = new RewritePatternRule();
		r.setPattern("/" + C.REVERSE_PROXY_PATH + "/*");
		r.setReplacement("/");
		rewrites.addRule(r);

		rewrites.setHandler(wrapped);
		return rewrites;
	}

	private static Connector createHttpConnector(final Server server, final InetAddress bindAddress, final int port) {
		final HttpConfiguration config = new HttpConfiguration();

		final HttpConnectionFactory http1 = new HttpConnectionFactory(config);
		final HTTP2CServerConnectionFactory http2 = new HTTP2CServerConnectionFactory(config);

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

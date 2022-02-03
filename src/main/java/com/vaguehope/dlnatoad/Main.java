package com.vaguehope.dlnatoad;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.resource.IconResource;
import org.fourthline.cling.model.resource.Resource;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.registry.Registry;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.akuma.Daemon;
import com.vaguehope.dlnatoad.db.MediaDb;
import com.vaguehope.dlnatoad.dlnaserver.ContentServingHistory;
import com.vaguehope.dlnatoad.dlnaserver.ContentServlet;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.dlnaserver.MediaServer;
import com.vaguehope.dlnatoad.dlnaserver.RegistryImplWithOverrides;
import com.vaguehope.dlnatoad.media.MediaFormat;
import com.vaguehope.dlnatoad.media.MediaId;
import com.vaguehope.dlnatoad.media.MediaIndex;
import com.vaguehope.dlnatoad.media.MediaIndex.HierarchyMode;
import com.vaguehope.dlnatoad.media.MediaInfo;
import com.vaguehope.dlnatoad.ui.IndexServlet;
import com.vaguehope.dlnatoad.ui.SearchServlet;
import com.vaguehope.dlnatoad.ui.ServletCommon;
import com.vaguehope.dlnatoad.ui.UpnpServlet;
import com.vaguehope.dlnatoad.util.DaemonThreadFactory;
import com.vaguehope.dlnatoad.util.ImageResizer;
import com.vaguehope.dlnatoad.util.LogHelper;
import com.vaguehope.dlnatoad.util.NetHelper;
import com.vaguehope.dlnatoad.util.NetHelper.IfaceAndAddr;
import com.vaguehope.dlnatoad.util.ProgressLogFileListener;
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
			daemonise(args);
			run(args);
		}
		catch (final CmdLineException e) {
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
		final String hostName = InetAddress.getLocalHost().getHostName();
		LOG.info("hostName: {}", hostName);

		final InetAddress address;
		if (args.getInterface() != null) {
			address = InetAddress.getByName(args.getInterface());
			LOG.info("using address: {}", address);
		}
		else {
			final List<IfaceAndAddr> addresses = NetHelper.getIpAddresses();
			address = addresses.iterator().next().getAddr();
			LOG.info("addresses: {} using address: {}", addresses, address);
		}

		final ScheduledExecutorService fsExSvc = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("fs", -2));
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run () {
				fsExSvc.shutdown();
			}
		});

		final ExecutorService miExSvc = new ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(), new DaemonThreadFactory("mi", -1));
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run () {
				miExSvc.shutdown();
			}
		});

		final File dbFile = args.getDb();
		final MediaDb mediaDb;
		if (dbFile != null) {
			LOG.info("db: {}", dbFile.getAbsolutePath());
			mediaDb = new MediaDb(dbFile, fsExSvc, args.isVerboseLog());
		}
		else {
			mediaDb = null;
		}
		final MediaId mediaId = new MediaId(mediaDb);
		final MediaInfo mediaInfo = new MediaInfo(mediaDb, miExSvc);

		final UpnpService upnpService = makeUpnpServer();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run () {
				upnpService.shutdown();
			}
		});

		final ContentTree contentTree = new ContentTree();
		final Server server = startContentServer(contentTree, mediaId, upnpService, args, hostName);

		final String externalHttpContext = "http://" + address.getHostAddress() + ":" + server.getConnectors()[0].getPort();
		final URI selfUri = new URI(externalHttpContext);
		LOG.info("Self: {}", externalHttpContext);

		final HierarchyMode hierarchyMode = args.isSimplifyHierarchy() ? HierarchyMode.FLATTERN : HierarchyMode.PRESERVE;
		LOG.info("hierarchyMode: {}", hierarchyMode);

		final MediaIndex index = new MediaIndex(contentTree, externalHttpContext, hierarchyMode, mediaId, mediaInfo);

		final Thread watcherThread = new Thread(new RunWatcher(args, index));
		watcherThread.setName("watcher");
		watcherThread.setDaemon(true);
		watcherThread.start();

		upnpService.getRegistry().addDevice(new MediaServer(contentTree, hostName, args.isPrintAccessLog(), selfUri).getDevice());
		upnpService.getControlPoint().search(); // In case this helps announce our presence.  Untested.
		server.join(); // Keep app alive.
	}

	private static UpnpService makeUpnpServer () throws IOException {
		final Map<String, Resource<?>> pathToRes = new HashMap<>();

		final Icon icon = MediaServer.createDeviceIcon();
		final IconResource iconResource = new IconResource(icon.getUri(), icon);
		pathToRes.put("/icon.png", iconResource);

		return new UpnpServiceImpl() {
			@Override
			protected Registry createRegistry (final ProtocolFactory pf) {
				return new RegistryImplWithOverrides(this, pathToRes);
			}
		};
	}

	private static Server startContentServer (final ContentTree contentTree, final MediaId mediaId, final UpnpService upnpService, final Args args, final String hostName) throws Exception {
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
			final HandlerList handler = makeContentHandler(contentTree, mediaId, upnpService, args, hostName);

			final Server server = new Server();
			server.setHandler(handler);
			server.addConnector(createHttpConnector(args.getInterface(), port));
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

	private static HandlerList makeContentHandler (final ContentTree contentTree, final MediaId mediaId, final UpnpService upnpService, final Args args, final String hostName) throws CmdLineException {
		final File thumbsDir = args.getThumbsDir();
		final ImageResizer imageResizer =
				thumbsDir != null
				? new ImageResizer(thumbsDir)
				: null;

		final ServletContextHandler servletHandler = new ServletContextHandler();
		MediaFormat.addTo(servletHandler.getMimeTypes());
		servletHandler.setContextPath("/");
		final ContentServingHistory contentServingHistory = new ContentServingHistory();

		final ContentServlet contentServlet = new ContentServlet(contentTree, contentServingHistory, args.isPrintAccessLog());
		servletHandler.addServlet(new ServletHolder(contentServlet), "/" + C.CONTENT_PATH_PREFIX + "*");

		final ServletCommon servletCommon = new ServletCommon(contentTree, mediaId, imageResizer, hostName, contentServingHistory);
		servletHandler.addServlet(new ServletHolder(new SearchServlet(servletCommon, contentTree)), "/search");
		servletHandler.addServlet(new ServletHolder(new UpnpServlet(servletCommon, upnpService)), "/upnp");
		servletHandler.addServlet(new ServletHolder(new IndexServlet(servletCommon, contentTree, contentServlet, args.isPrintAccessLog())), "/*");

		final HandlerList handler = new HandlerList();
		handler.setHandlers(new Handler[] { servletHandler });
		return handler;
	}

	private static SelectChannelConnector createHttpConnector (final String iface, final int port) {
		final SelectChannelConnector connector = new SelectChannelConnector();
		connector.setStatsOn(false);
		connector.setHost(iface);
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

		public RunWatcher (final Args args, final MediaIndex index) throws CmdLineException, IOException {
			this.roots = args.getDirs();  // Trigger validation in main thread.
			this.verboseLog = args.isVerboseLog();
			this.index = index;
		}

		@Override
		public void run () {
			try {
				new Watcher(this.roots, MediaFormat.MediaFileFilter.INSTANCE,
						new ProgressLogFileListener(this.index, this.verboseLog)).run();
				LOG.error("Watcher thread exited.");
			}
			catch (final Exception e) { // NOSONAR
				LOG.error("Watcher thread died.", e);
			}
		}

	}

}

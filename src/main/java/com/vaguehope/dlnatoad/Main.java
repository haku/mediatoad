package com.vaguehope.dlnatoad;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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
import com.vaguehope.dlnatoad.util.DaemonThreadFactory;
import com.vaguehope.dlnatoad.util.ImageResizer;
import com.vaguehope.dlnatoad.util.LogHelper;
import com.vaguehope.dlnatoad.util.NetHelper;
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
			final List<InetAddress> addresses = NetHelper.getIpAddresses();
			address = addresses.iterator().next();
			LOG.info("addresses: {} using address: {}", addresses, address);
		}

		final ScheduledExecutorService fsExSvc = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("fs"));
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run () {
				fsExSvc.shutdown();
			}
		});

		final File dbFile = args.getDb();
		final MediaDb mediaDb;
		if (dbFile != null) {
			LOG.info("db: {}", dbFile.getAbsolutePath());
			mediaDb = new MediaDb(dbFile, fsExSvc);
		}
		else {
			mediaDb = null;
		}
		final MediaId mediaId = new MediaId(mediaDb);
		final MediaInfo mediaInfo = new MediaInfo(mediaDb, fsExSvc);

		final ContentTree contentTree = new ContentTree();
		final Server server = startContentServer(contentTree, mediaId, args);

		final String externalHttpContext = "http://" + address.getHostAddress() + ":" + C.HTTP_PORT;

		final HierarchyMode hierarchyMode = args.isPreserveHierarchy() ? HierarchyMode.PRESERVE : HierarchyMode.FLATTERN;
		LOG.info("hierarchyMode: {}", hierarchyMode);

		final MediaIndex index = new MediaIndex(contentTree, externalHttpContext, hierarchyMode, mediaId, mediaInfo);

		final Thread watcherThread = new Thread(new RunWatcher(args.getDirs(), index));
		watcherThread.setName("watcher");
		watcherThread.setDaemon(true);
		watcherThread.start();

		final UpnpService upnpService = makeUpnpServer();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run () {
				upnpService.shutdown();
			}
		});
		upnpService.getRegistry().addDevice(new MediaServer(contentTree, hostName, args.isPrintAccessLog()).getDevice());
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
			protected Registry createRegistry (final ProtocolFactory protocolFactory) {
				return new RegistryImplWithOverrides(this, pathToRes);
			}
		};
	}

	private static Server startContentServer (final ContentTree contentTree, final MediaId mediaId, final Args args) throws Exception {
		int port = C.HTTP_PORT;
		while (true) {
			final HandlerList handler = makeContentHandler(contentTree, mediaId, args);

			final Server server = new Server();
			server.setHandler(handler);
			server.addConnector(createHttpConnector(args.getInterface(), port));
			try {
				server.start();
				return server;
			}
			catch (final BindException e) {
				if ("Address already in use".equals(e.getMessage())) {
					port += 1;
				}
				else {
					throw e;
				}
			}
		}
	}

	private static HandlerList makeContentHandler (final ContentTree contentTree, final MediaId mediaId, final Args args) throws CmdLineException {
		final File thumbsDir = args.getThumbsDir();
		final ImageResizer imageResizer =
				thumbsDir != null
				? new ImageResizer(thumbsDir)
				: null;

		final ServletContextHandler servletHandler = new ServletContextHandler();
		servletHandler.setContextPath("/");
		servletHandler.addServlet(new ServletHolder(new ContentServlet(contentTree, args.isPrintAccessLog())), "/");
		servletHandler.addServlet(new ServletHolder(new IndexServlet(contentTree, mediaId, imageResizer)), "/index/*");

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
		private final MediaIndex index;

		public RunWatcher (final List<File> roots, final MediaIndex index) {
			this.roots = roots;
			this.index = index;
		}

		@Override
		public void run () {
			try {
				new Watcher(this.roots, MediaFormat.MediaFileFilter.INSTANCE,
						new ProgressLogFileListener(this.index)).run();
				LOG.error("Watcher thread exited.");
			}
			catch (final Exception e) { // NOSONAR
				LOG.error("Watcher thread died.", e);
			}
		}

	}

}

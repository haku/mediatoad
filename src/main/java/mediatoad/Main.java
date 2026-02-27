package mediatoad;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.jupnp.UpnpService;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.akuma.Daemon;

import io.prometheus.metrics.instrumentation.jvm.JvmMemoryMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import mediatoad.Args.ArgsException;
import mediatoad.auth.Authoriser;
import mediatoad.auth.Users;
import mediatoad.auth.UsersCli;
import mediatoad.db.DbCache;
import mediatoad.db.DbCleaner;
import mediatoad.db.MediaDb;
import mediatoad.db.MediaMetadataStore;
import mediatoad.db.TagAutocompleter;
import mediatoad.dlnaserver.DlnaService;
import mediatoad.dlnaserver.MediaServer;
import mediatoad.dlnaserver.NodeConverter;
import mediatoad.dlnaserver.SystemId;
import mediatoad.httpserver.HttpServer;
import mediatoad.importer.MetadataImporter;
import mediatoad.media.ContentTree;
import mediatoad.media.ExternalUrls;
import mediatoad.media.MediaFormat;
import mediatoad.media.MediaId;
import mediatoad.media.MediaIndex;
import mediatoad.media.MediaIndex.HierarchyMode;
import mediatoad.media.MediaInfo;
import mediatoad.media.ThumbnailGenerator;
import mediatoad.rpc.client.RpcClient;
import mediatoad.tagdeterminer.TagDeterminerController;
import mediatoad.util.ExecutorHelper;
import mediatoad.util.LogHelper;
import mediatoad.util.NetHelper;
import mediatoad.util.ProgressLogFileListener;
import mediatoad.util.Watcher;

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

		final File userfile = args.getUserfile();
		final Users users = userfile != null ? new Users(userfile) : null;
		final Authoriser authoriser = new Authoriser(args.getDefaultOpenHttp(), users);

		final RpcClient rpcClient = new RpcClient(args);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				rpcClient.shutdown();
			}
		});
		rpcClient.start();

		final UpnpService upnpService = new DlnaService(bindAddresses).start();
		final Server httpServer = new HttpServer(
				contentTree, mediaDb, dbCache, tagAutocompleter, upnpService, users, rpcClient, thumbnailGenerator, args, bindAddresses, hostName)
						.start();

		final ExternalUrls externalUrls = new ExternalUrls(selfAddress, ((ServerConnector) httpServer.getConnectors()[0]).getPort());
		LOG.info("Self: {}", externalUrls.getSelfUri());

		final NodeConverter nodeConverter = new NodeConverter(externalUrls);

		final HierarchyMode hierarchyMode = args.isSimplifyHierarchy() ? HierarchyMode.FLATTERN : HierarchyMode.PRESERVE;
		LOG.info("hierarchyMode: {}", hierarchyMode);

		final MediaIndex index = new MediaIndex(contentTree, hierarchyMode, mediaId, mediaInfo, authoriser, args.isVerboseLog());

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

		httpServer.join(); // Keep app alive.
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

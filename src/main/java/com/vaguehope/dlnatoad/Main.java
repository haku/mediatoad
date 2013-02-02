package com.vaguehope.dlnatoad;

import java.io.File;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.List;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;

import com.vaguehope.dlnatoad.dlnaserver.ContentServlet;
import com.vaguehope.dlnatoad.dlnaserver.ContentTree;
import com.vaguehope.dlnatoad.dlnaserver.DeviceWatcher;
import com.vaguehope.dlnatoad.dlnaserver.MediaServer;
import com.vaguehope.dlnatoad.media.MediaIndex;
import com.vaguehope.dlnatoad.util.LogHelper;
import com.vaguehope.dlnatoad.util.NetHelper;

public final class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

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
			run(args.getDirs());
		}
		catch (CmdLineException e) {
			err.println(e.getMessage());
			help(parser, err);
			return;
		}
		catch (Exception e) {
			err.println("An unhandled error occured.");
			e.printStackTrace(err);
		}
	}

	private static void run (final List<File> dirs) throws Exception { // NOSONAR
		final String hostName = InetAddress.getLocalHost().getHostName();
		final List<InetAddress> addresses = NetHelper.getIpAddresses();
		final InetAddress address = addresses.iterator().next();
		LOG.info("hostName: {}", hostName);
		LOG.info("addresses: {} useing address: {}", addresses, address);

		final UpnpService upnpService = new UpnpServiceImpl();
		upnpService.getRegistry().addListener(new DeviceWatcher());
		upnpService.getControlPoint().search();

		final ContentTree contentTree = new ContentTree();
		upnpService.getRegistry().addDevice(new MediaServer(contentTree, hostName).getDevice());
		final Server server = makeContentServer(contentTree);
		server.start();

		final String externalHttpContext = "http://" + address.getHostAddress() + ":" + C.HTTP_PORT;
		new MediaIndex(dirs, contentTree, externalHttpContext).refresh();

		server.join(); // Keep app alive.
	}

	private static Server makeContentServer (final ContentTree contentTree) {
		final ServletContextHandler servletHandler = new ServletContextHandler();
		servletHandler.setContextPath("/");
		servletHandler.addServlet(new ServletHolder(new ContentServlet(contentTree)), "/");

		final HandlerList handler = new HandlerList();
		handler.setHandlers(new Handler[] { servletHandler });

		final Server server = new Server();
		server.setHandler(handler);
		server.addConnector(createHttpConnector(C.HTTP_PORT));
		return server;
	}

	private static SelectChannelConnector createHttpConnector (final int port) {
		final SelectChannelConnector connector = new SelectChannelConnector();
		connector.setStatsOn(false);
		connector.setPort(port);
		return connector;
	}

	private static void help (CmdLineParser parser, PrintStream ps) {
		ps.print("Usage: ");
		ps.print(C.APPNAME);
		parser.printSingleLineUsage(ps);
		ps.println();
		parser.printUsage(ps);
		ps.println();
	}

}

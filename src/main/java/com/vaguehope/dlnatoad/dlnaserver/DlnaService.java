package com.vaguehope.dlnatoad.dlnaserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.Servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.meta.Icon;
import org.jupnp.model.resource.IconResource;
import org.jupnp.model.resource.Resource;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.registry.Registry;
import org.jupnp.transport.impl.NetworkAddressFactoryImpl;
import org.jupnp.transport.impl.ServletStreamServerConfigurationImpl;
import org.jupnp.transport.impl.ServletStreamServerImpl;
import org.jupnp.transport.impl.jetty.Jetty10StreamClientImpl;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.ServletContainerAdapter;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

public class DlnaService {

	private final List<InetAddress> addressesToBind;  // Named this way cos NetworkAddressFactoryImpl has a bindAddresses field.
	private final Map<String, Resource<?>> registryPathToRes = new HashMap<>();

	public DlnaService(final List<InetAddress> bindAddresses) throws IOException {
		this.addressesToBind = bindAddresses;
		final Icon icon = MediaServer.createDeviceIcon();
		final IconResource iconResource = new IconResource(icon.getUri(), icon);
		this.registryPathToRes.put("/icon.png", iconResource);
	}

	public UpnpService start() {
		final MyUpnpService upnpService = new MyUpnpService(new MyUpnpServiceConfiguration());
		upnpService.startup();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				upnpService.shutdown();
			}
		});

		return upnpService;
	}

	private class MyUpnpService extends UpnpServiceImpl {

		private MyUpnpService(final UpnpServiceConfiguration configuration) {
			super(configuration);
		}

		@Override
		protected Registry createRegistry (final ProtocolFactory pf) {
			return new RegistryImplWithOverrides(this, DlnaService.this.registryPathToRes);
		}

	}

	private class MyUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {

		@Override
		protected NetworkAddressFactory createNetworkAddressFactory(final int streamListenPort, final int multicastResponsePort) {
			return new MyNetworkAddressFactory(streamListenPort, multicastResponsePort);
		}

		private final ServletContainerAdapter jettyAdaptor = new MyJettyServletContainer();

		// Workaround for https://github.com/jupnp/jupnp/issues/225
		// TODO remove this override once it is fixed.
		@Override
		public StreamServer createStreamServer(final NetworkAddressFactory networkAddressFactory) {
			return new ServletStreamServerImpl(new ServletStreamServerConfigurationImpl(this.jettyAdaptor, networkAddressFactory.getStreamListenPort()));
		}

		// Workaround for jupnp not being compatible with Jetty 10.
		// TODO remove this and the edited classes when jupnp uses Jetty 10.
		@Override
		public StreamClient createStreamClient() {
			// values from org.jupnp.transport.spi.AbstractStreamClientConfiguration.
			StreamClientConfigurationImpl clientConfiguration = new StreamClientConfigurationImpl(
					getSyncProtocolExecutorService(),
					/* timeoutSeconds */ 10,
					/* logWarningSeconds */ 5,
					/* retryAfterSeconds */ (int) TimeUnit.MINUTES.toSeconds(10),
					/* retryIterations */ 5);

			return new Jetty10StreamClientImpl(clientConfiguration);
		}

	}

	private class MyNetworkAddressFactory extends NetworkAddressFactoryImpl {

		private MyNetworkAddressFactory(final int streamListenPort, final int multicastResponsePort) throws InitializationException {
			super(streamListenPort, multicastResponsePort);
		}

		@Override
		protected boolean isUsableAddress(final NetworkInterface iface, final InetAddress address) {
			if (DlnaService.this.addressesToBind == null) {
				return super.isUsableAddress(iface, address);
			}
			return DlnaService.this.addressesToBind.contains(address);
		}

	}

	private class MyJettyServletContainer implements ServletContainerAdapter {

		protected Server server;

		public MyJettyServletContainer() {
			resetServer();
		}

		@Override
		public void setExecutorService(final ExecutorService executorService) {
			// not needed.
		}

		@SuppressWarnings("resource")
		@Override
		public int addConnector(final String host, final int port) throws IOException {
			final ServerConnector connector = new ServerConnector(this.server);
			connector.setHost(host);
			connector.setPort(port);
			connector.open();
			this.server.addConnector(connector);
			return connector.getLocalPort();
		}

		@Override
		public void registerServlet(final String contextPath, final Servlet servlet) {
			if (this.server.getHandler() != null) {
				return;
			}
			final ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
			if (contextPath != null && !contextPath.isEmpty()) {
				servletHandler.setContextPath(contextPath);
			}
			final ServletHolder s = new ServletHolder(servlet);
			servletHandler.addServlet(s, "/*");
			this.server.setHandler(servletHandler);
		}

		@Override
		public synchronized void startIfNotRunning() {
			if (!this.server.isStarted() && !this.server.isStarting()) {
				try {
					this.server.start();
				}
				catch (final Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override
		public synchronized void stopIfRunning() {
			if (!this.server.isStopped() && !this.server.isStopping()) {
				try {
					this.server.stop();
				}
				catch (final Exception e) {
					throw new RuntimeException(e);
				}
				finally {
					resetServer();
				}
			}
		}

		protected void resetServer() {
			this.server = new Server();
		}

	}

}

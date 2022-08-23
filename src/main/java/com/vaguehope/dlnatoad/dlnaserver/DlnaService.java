package com.vaguehope.dlnatoad.dlnaserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.DefaultUpnpServiceConfiguration;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceConfiguration;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.resource.IconResource;
import org.fourthline.cling.model.resource.Resource;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.transport.impl.NetworkAddressFactoryImpl;
import org.fourthline.cling.transport.spi.InitializationException;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;

import com.vaguehope.dlnatoad.util.DaemonThreadFactory;

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
		protected NetworkAddressFactory createNetworkAddressFactory(final int streamListenPort) {
			return new MyNetworkAddressFactory(streamListenPort);
		}

		@Override
		protected ExecutorService createDefaultExecutorService() {
			return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 30L, TimeUnit.SECONDS,
					new SynchronousQueue<Runnable>(), new DaemonThreadFactory("upnp"));
		}

	}

	private class MyNetworkAddressFactory extends NetworkAddressFactoryImpl {

		private MyNetworkAddressFactory(final int streamListenPort) throws InitializationException {
			super(streamListenPort);
		}

		@Override
		protected boolean isUsableAddress(final NetworkInterface iface, final InetAddress address) {
			if (DlnaService.this.addressesToBind == null) {
				return super.isUsableAddress(iface, address);
			}
			return DlnaService.this.addressesToBind.contains(address);
		}

	}

}

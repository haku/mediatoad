package com.vaguehope.dlnatoad.dlnaserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;

public class DeviceWatcher extends DefaultRegistryListener {

	private static final Logger LOG = LoggerFactory.getLogger(DeviceWatcher.class);

	public DeviceWatcher () {}

	@Override
	public void remoteDeviceAdded (final Registry registry, final RemoteDevice device) {
		LOG.info("found: {} {} {}",
				device.getIdentity().getDescriptorURL().getHost(),
				device.getDisplayString(),
				serviceTypes(device).toString()
				);
	}

	@Override
	public void remoteDeviceRemoved (final Registry registry, final RemoteDevice device) {
		LOG.info("lost: {} {}",
				device.getIdentity().getDescriptorURL().getHost(),
				device.getDisplayString()
				);
	}

	private static StringBuilder serviceTypes (final RemoteDevice device) {
		final StringBuilder svcsDes = new StringBuilder("[");
		final RemoteService[] svcs = device.getServices();
		for (final RemoteService svc : svcs) {
			if (svcsDes.length() > 1) svcsDes.append(", ");
			svcsDes.append(svc.getServiceType().getType());
		}
		svcsDes.append("]");
		return svcsDes;
	}

}

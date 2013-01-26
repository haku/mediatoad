package com.vaguehope.dlnatoad.dlnaserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;

public class DeviceWatcher extends DefaultRegistryListener {

	private static final Logger LOG = LoggerFactory.getLogger(DeviceWatcher.class);

	public DeviceWatcher () {}

	@Override
	public void remoteDeviceAdded (Registry registry, RemoteDevice device) {
		logEvent(device, "found");
	}

	@Override
	public void remoteDeviceRemoved (Registry registry, RemoteDevice device) {
		logEvent(device, "lost");
	}

	private static void logEvent (RemoteDevice device, String verb) {
		LOG.info(verb + ": {} {}", device.getClass().getSimpleName(), device.getIdentity().getDescriptorURL().getHost());
	}

}
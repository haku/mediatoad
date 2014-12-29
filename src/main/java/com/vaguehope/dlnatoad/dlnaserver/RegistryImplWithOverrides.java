package com.vaguehope.dlnatoad.dlnaserver;

import java.net.URI;
import java.util.Map;

import org.teleal.cling.UpnpService;
import org.teleal.cling.model.resource.Resource;
import org.teleal.cling.registry.RegistryImpl;

public class RegistryImplWithOverrides extends RegistryImpl {

	private final Map<String, Resource<?>> pathToResource;

	public RegistryImplWithOverrides (final UpnpService upnpService, final Map<String, Resource<?>> pathToResource) {
		super(upnpService);
		this.pathToResource = pathToResource;
	}

	@Override
	public synchronized Resource getResource (final URI pathQuery) throws IllegalArgumentException {
		final Resource<?> res = this.pathToResource.get(pathQuery.getPath());
		if (res != null) return res;
		return super.getResource(pathQuery);
	}

}
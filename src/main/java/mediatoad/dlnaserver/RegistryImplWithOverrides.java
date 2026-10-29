package mediatoad.dlnaserver;

import java.net.URI;
import java.util.Map;

import org.jupnp.UpnpService;
import org.jupnp.model.resource.Resource;
import org.jupnp.registry.RegistryImpl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class RegistryImplWithOverrides extends RegistryImpl {

	private final Map<String, Resource<?>> pathToResource;

	@SuppressFBWarnings(value = "EI_EXPOSE_REP2")
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
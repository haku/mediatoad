package com.vaguehope.dlnatoad.dlnaserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.teleal.cling.model.DefaultServiceManager;
import org.teleal.cling.model.ValidationException;
import org.teleal.cling.model.meta.DeviceDetails;
import org.teleal.cling.model.meta.DeviceIdentity;
import org.teleal.cling.model.meta.LocalDevice;
import org.teleal.cling.model.meta.LocalService;
import org.teleal.cling.model.meta.ManufacturerDetails;
import org.teleal.cling.model.meta.ModelDetails;
import org.teleal.cling.model.types.DeviceType;
import org.teleal.cling.model.types.UDADeviceType;
import org.teleal.cling.model.types.UDN;
import org.teleal.cling.support.connectionmanager.ConnectionManagerService;

import com.vaguehope.dlnatoad.C;

/**
 * Based on a class from WireMe and used under Apache 2 License.
 * See https://code.google.com/p/wireme/ for more details.
 */
public class MediaServer {

	private static final String DEVICE_TYPE = "MediaServer";
	private static final int VERSION = 1;
	private static final Logger LOG = LoggerFactory.getLogger(MediaServer.class);

	private final LocalDevice localDevice;

	public MediaServer (final ContentTree contentTree, final String hostName) throws ValidationException {
		final DeviceType type = new UDADeviceType(DEVICE_TYPE, VERSION);
		final DeviceDetails details = new DeviceDetails(C.METADATA_MODEL_NAME + " (" + hostName + ")",
				new ManufacturerDetails(C.METADATA_MANUFACTURER),
				new ModelDetails(C.METADATA_MODEL_NAME, C.METADATA_MODEL_DESCRIPTION, C.METADATA_MODEL_NUMBER));

		final LocalService<ContentDirectoryService> contDirSrv = new AnnotationLocalServiceBinder().read(ContentDirectoryService.class);
		contDirSrv.setManager(new DefaultServiceManager<ContentDirectoryService>(contDirSrv, ContentDirectoryService.class) {
			@Override
			protected ContentDirectoryService createServiceInstance () {
				return new ContentDirectoryService(contentTree);
			}
		});

		final LocalService<ConnectionManagerService> connManSrv = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
		connManSrv.setManager(new DefaultServiceManager<ConnectionManagerService>(connManSrv, ConnectionManagerService.class));

		final UDN usi = UDN.uniqueSystemIdentifier("DLNAtoad-MediaServer");
		LOG.info("uniqueSystemIdentifier: {}", usi);
		this.localDevice = new LocalDevice(new DeviceIdentity(usi), type, details, new LocalService[] { contDirSrv, connManSrv });
	}

	public LocalDevice getDevice () {
		return this.localDevice;
	}

}

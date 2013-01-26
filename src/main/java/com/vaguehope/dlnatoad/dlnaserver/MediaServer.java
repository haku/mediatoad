package com.vaguehope.dlnatoad.dlnaserver;

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

import com.vaguehope.dlnatoad.C;

/**
 * Based on a class from WireMe and used under Apache 2 License.
 * See https://code.google.com/p/wireme/ for more details.
 */
public class MediaServer {

	private static final String DEVICE_TYPE = "MediaServer";
	private static final int VERSION = 1;

	private final LocalDevice localDevice;

	public MediaServer (final ContentTree contentTree, final String hostName) throws ValidationException {
		final DeviceType type = new UDADeviceType(DEVICE_TYPE, VERSION);
		final DeviceDetails details = new DeviceDetails(C.METADATA_MODEL_NAME + " (" + hostName + ")",
				new ManufacturerDetails(C.METADATA_MANUFACTURER),
				new ModelDetails(C.METADATA_MODEL_NAME, C.METADATA_MODEL_DESCRIPTION, C.METADATA_MODEL_NUMBER));

		final LocalService<ContentDirectoryService> service = new AnnotationLocalServiceBinder().read(ContentDirectoryService.class);
		service.setManager(new DefaultServiceManager<ContentDirectoryService>(service, ContentDirectoryService.class) {
			@Override
			protected ContentDirectoryService createServiceInstance () {
				return new ContentDirectoryService(contentTree);
			}
		});
		this.localDevice = new LocalDevice(new DeviceIdentity(UDN.uniqueSystemIdentifier("DLNAtoad-MediaServer")), type, details, service);
	}

	public LocalDevice getDevice () {
		return this.localDevice;
	}

}

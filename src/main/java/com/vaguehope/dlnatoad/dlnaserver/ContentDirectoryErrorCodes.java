package com.vaguehope.dlnatoad.dlnaserver;

public interface ContentDirectoryErrorCodes {

	// error codes from UPnP-av-ContentDirectory-v1-Service
	static final int NO_SUCH_OBJECT = 701; // The specified ObjectID is invalid.
	static final int UNSUPPORTED_SEARCH_CRITERIA = 708; // The search criteria specified is not supported or is invalid.
	static final int UNSUPPORTED_SORT_CRITERIA = 709; // The sort criteria specified is not supported or is invalid.
	static final int UNSUPPORTED_SEARCH_CONTAINER = 710; // The specified ContainerID is invalid or identifies an object that is not a container.
	static final int RESTRICTED_OBJECT = 711; // Operation failed because the restricted attribute of object is set to true.
	static final int BAD_METADATA = 712; // Operation fails because it would result in invalid or disallowed metadata in current object.
	static final int RESTRICTED_PARENT_OBJECT = 713; // Operation failed because the restricted attribute of parent object is set to true.
	static final int CANNOT_PROCESS = 720; // Cannot process the request.

}

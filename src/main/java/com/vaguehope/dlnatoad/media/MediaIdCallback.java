package com.vaguehope.dlnatoad.media;

import java.io.IOException;

public interface MediaIdCallback {

	void onMediaId(String mediaId) throws IOException;

	void onError(Exception e);

}

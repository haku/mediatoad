package com.vaguehope.dlnatoad.media;

import java.io.IOException;

import com.vaguehope.dlnatoad.util.AsyncCallback;

public interface MediaIdCallback extends AsyncCallback<String, IOException> {

	@Override
	void onResult(String mediaId) throws IOException;

	@Override
	void onError(IOException e);

}

package com.vaguehope.dlnatoad.media;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

import com.vaguehope.dlnatoad.C;

public class ExternalUrls {

	private final String selfUriString;
	private final URI selfUri;

	public ExternalUrls(final InetAddress address, final int port) throws URISyntaxException {
		this("http://" + address.getHostAddress() + ":" + port);
	}

	public ExternalUrls(final String externalHttpContext) throws URISyntaxException {
		this.selfUriString = externalHttpContext;
		this.selfUri = new URI(externalHttpContext);
	}

	public URI getSelfUri() {
		return this.selfUri;
	}

	public String contentUrl(final String id) {
		return this.selfUriString + "/" + C.CONTENT_PATH_PREFIX + id;
	}

}

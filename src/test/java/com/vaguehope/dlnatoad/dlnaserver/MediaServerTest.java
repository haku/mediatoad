package com.vaguehope.dlnatoad.dlnaserver;

import java.net.URI;

import org.junit.Test;

public class MediaServerTest {

	@Test
	public void itDoesSomething () throws Exception {
		new MediaServer(new ContentTree(), "hostname", true, URI.create("http://example.com:12345"));
	}

}

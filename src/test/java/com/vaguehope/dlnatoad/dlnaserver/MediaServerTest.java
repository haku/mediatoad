package com.vaguehope.dlnatoad.dlnaserver;

import java.net.URI;

import org.junit.Test;

import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.media.ExternalUrls;

public class MediaServerTest {

	@Test
	public void itDoesSomething () throws Exception {
		new MediaServer(new ContentTree(), new NodeConverter(new ExternalUrls("")), "hostname", true, URI.create("http://example.com:12345"));
	}

}

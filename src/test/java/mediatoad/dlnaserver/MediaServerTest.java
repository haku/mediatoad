package mediatoad.dlnaserver;

import java.net.URI;

import org.junit.Test;

import mediatoad.Args;
import mediatoad.media.ContentTree;
import mediatoad.media.ExternalUrls;

public class MediaServerTest {

	@SuppressWarnings("unused")
	@Test
	public void itDoesSomething () throws Exception {
		new MediaServer(new SystemId(new Args()), new ContentTree(), new NodeConverter(new ExternalUrls("")), "hostname", true, URI.create("http://example.com:12345"));
	}

}

package com.vaguehope.dlnatoad.dlnaserver;

import org.junit.Test;

public class MediaServerTest {

	@Test
	public void itDoesSomething () throws Exception {
		new MediaServer(new ContentTree(), "hostname", true);
	}

}

package com.vaguehope.dlnatoad.rpc.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.vaguehope.dlnatoad.Args;
import com.vaguehope.dlnatoad.rpc.RpcTarget;

public class RpcClientTest {

	@Test
	public void itParsesRemotes() throws Exception {
		final List<String> input = Arrays.asList(
				"http://example.com",
				"https://example.com",
				"https://example.com:12345");
		final List<RemoteInstance> expected = Arrays.asList(
				new RemoteInstance("0", new RpcTarget("dns:///example.com:80/", true)),
				new RemoteInstance("1", new RpcTarget("dns:///example.com:443/", false)),
				new RemoteInstance("2", new RpcTarget("dns:///example.com:12345/", false)));

		final Args args = mock(Args.class);
		when(args.getRemotes()).thenReturn(input);
		final RpcClient undertest = new RpcClient(args);
		assertEquals(expected, undertest.getRemoteInstances());
	}

}

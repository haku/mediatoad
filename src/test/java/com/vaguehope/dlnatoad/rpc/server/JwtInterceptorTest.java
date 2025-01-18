package com.vaguehope.dlnatoad.rpc.server;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Key;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ProtectedHeader;

public class JwtInterceptorTest {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void itVerifysTokenAndSetsContextValue() throws Exception {
		final Locator<Key> loader = mock(Locator.class);
		final JwtInterceptor undertest = new JwtInterceptor(loader);

		final KeyPair pair = Jwts.SIG.ES512.keyPair().build();
		when(loader.locate(isA(Header.class))).thenReturn(pair.getPublic());

		final String rawJws = Jwts.builder()
				.expiration(Date.from(Instant.now().plusSeconds(30)))
				.subject("alice")
				.header().add("username", "alice").and()
				.signWith(pair.getPrivate(), Jwts.SIG.ES512)
				.compact();

		final Metadata metadata = new Metadata();
		metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer" + rawJws);

		final ServerCall serverCall = mock(ServerCall.class);
		final ServerCallHandler serverCallHandler = mock(ServerCallHandler.class);
		AtomicReference<String> nameInContext = new AtomicReference<>();
		when(serverCallHandler.startCall(serverCall, metadata)).thenAnswer((a) -> {
			nameInContext.set( JwtInterceptor.USERNAME_CONTEXT_KEY.get());
			return null;
		});

		undertest.interceptCall(serverCall, metadata, serverCallHandler);

		verify(serverCallHandler).startCall(serverCall, metadata);
		verify(serverCall, times(0)).close(any(Status.class), any(Metadata.class));
		assertEquals("alice", nameInContext.get());

		final ArgumentCaptor<ProtectedHeader> cap = ArgumentCaptor.forClass(ProtectedHeader.class);
		verify(loader).locate(cap.capture());
		assertEquals("alice", cap.getValue().get("username"));
	}

}

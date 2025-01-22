package com.vaguehope.dlnatoad.rpc.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.collect.ImmutableSet;
import com.vaguehope.dlnatoad.auth.Permission;
import com.vaguehope.dlnatoad.auth.Users;
import com.vaguehope.dlnatoad.auth.Users.User;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class JwtInterceptorTest {

	private static final String USERNAME = "user@hostname";

	private JwkLoader loader;
	private Users users;
	private JwtInterceptor undertest;

	private ServerCall serverCall;
	private ServerCallHandler serverCallHandler;

	@Before
	public void before() throws Exception {
		this.loader = mock(JwkLoader.class);
		this.users = mock(Users.class);
		this.undertest = new JwtInterceptor(this.loader, this.users);

		this.serverCall = mock(ServerCall.class);
		this.serverCallHandler = mock(ServerCallHandler.class);
	}

	@Test
	public void itVerifysTokenAndSetsContextValue() throws Exception {
		final KeyPair pair = Jwts.SIG.ES512.keyPair().build();
		when(this.loader.locate(isA(Header.class))).thenReturn(pair.getPublic());

		final Metadata metadata = makeMetadataWithJws(makeJws(pair, false));

		final AtomicReference<String> nameInContext = new AtomicReference<>();
		final AtomicReference<Set<Permission>> permissionsInContext = new AtomicReference<>();
		when(this.serverCallHandler.startCall(this.serverCall, metadata)).thenAnswer((a) -> {
			nameInContext.set(JwtInterceptor.USERNAME_CONTEXT_KEY.get());
			permissionsInContext.set(JwtInterceptor.PERMISSIONS_CONTEXT_KEY.get());
			return null;
		});

		this.undertest.interceptCall(this.serverCall, metadata, this.serverCallHandler);

		verify(this.serverCallHandler).startCall(this.serverCall, metadata);
		verify(this.serverCall, times(0)).close(any(Status.class), any(Metadata.class));
		assertEquals(USERNAME, nameInContext.get());

		final ArgumentCaptor<Header> cap = ArgumentCaptor.forClass(Header.class);
		verify(this.loader).locate(cap.capture());
		assertEquals(USERNAME, cap.getValue().get("username"));

		assertThat(permissionsInContext.get(), hasSize(0));
		when(this.users.getUser(USERNAME)).thenReturn(new User(null, null, ImmutableSet.of(Permission.EDITTAGS)));
		this.undertest.interceptCall(this.serverCall, metadata, this.serverCallHandler);
		assertEquals(ImmutableSet.of(Permission.EDITTAGS), permissionsInContext.get());
	}

	@Test
	public void itIgnoresUnknownJwtsWithoutPublicKeys() throws Exception {
		final KeyPair pair = Jwts.SIG.ES512.keyPair().build();
		final Metadata metadata = makeMetadataWithJws(makeJws(pair, false));

		this.undertest.interceptCall(this.serverCall, metadata, this.serverCallHandler);
		verifyCloseStatus(Code.UNAUTHENTICATED);
	}

	@Test
	public void itCollectsPublicKeysOfRejectedCredentials() throws Exception {
		final KeyPair pair = Jwts.SIG.ES512.keyPair().build();
		final Metadata metadata = makeMetadataWithJws(makeJws(pair, true));

		this.undertest.interceptCall(this.serverCall, metadata, this.serverCallHandler);
		verifyCloseStatus(Code.UNAUTHENTICATED);
		verify(this.loader).recordRejectedPublicKey(USERNAME, Jwks.builder().key(pair.getPublic()).build());
	}

	private void verifyCloseStatus(final Code code) {
		final ArgumentCaptor<Status> cap = ArgumentCaptor.forClass(Status.class);
		verify(this.serverCall).close(cap.capture(), any(Metadata.class));
		assertEquals(code, cap.getValue().getCode());
	}

	private static Metadata makeMetadataWithJws(final String rawJws) {
		final Metadata metadata = new Metadata();
		metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer" + rawJws);
		return metadata;
	}

	private static String makeJws(final KeyPair pair, final boolean includePublicKey) {
		final PublicJwk<?> publicJwk = Jwks.builder().key(pair.getPublic()).build();
		final JwtBuilder jwtBuilder = Jwts.builder()
				.header().add("username", USERNAME).and()
				.expiration(Date.from(Instant.now().plusSeconds(30)))
				.subject(USERNAME);
		if (includePublicKey) jwtBuilder.header().add("jwk", publicJwk).and();
		final String rawJws = jwtBuilder
				.signWith(pair.getPrivate(), Jwts.SIG.ES512)
				.compact();
		return rawJws;
	}

}

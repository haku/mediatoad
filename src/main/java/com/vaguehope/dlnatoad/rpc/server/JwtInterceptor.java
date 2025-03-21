package com.vaguehope.dlnatoad.rpc.server;

import java.security.Key;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableSet;
import com.vaguehope.dlnatoad.auth.Permission;
import com.vaguehope.dlnatoad.auth.Users;
import com.vaguehope.dlnatoad.auth.Users.User;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.PublicJwk;

public class JwtInterceptor implements ServerInterceptor {

	private static final String HEADER_USERNAME = "username";
	private static final String HEADER_JWK = "jwk";

	static final Context.Key<String> USERNAME_CONTEXT_KEY = Context.key(HEADER_USERNAME);
	static final Context.Key<Set<Permission>> PERMISSIONS_CONTEXT_KEY = Context.key("PERMISSIONS");

	private static final String BEARER_TYPE = "Bearer";
	private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

	private final JwkLoader loader;
	private final Users users;
	private final JwtParser secureParser;
	private final JwtParser insecureParser;

	public JwtInterceptor(final JwkLoader loader, final Users users) {
		this.loader = loader;
		this.users = users;
		this.secureParser = Jwts.parser()
				.keyLocator(loader)
				.build();
		this.insecureParser = Jwts.parser()
				.keyLocator(new SelfSignedLocator())
				.build();
	}

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			final ServerCall<ReqT, RespT> serverCall,
			final Metadata metadata,
			final ServerCallHandler<ReqT, RespT> serverCallHandler) {

		final String value = metadata.get(AUTHORIZATION_METADATA_KEY);
		if (value == null) {
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription("Authorization token is missing."));
		}
		if (!value.startsWith(BEARER_TYPE)) {
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription("Unknown authorization type."));
		}
		final String rawJwt = value.substring(BEARER_TYPE.length()).trim();

		final Jws<Claims> claims;
		try {
			claims = this.secureParser.parseSignedClaims(rawJwt);
		}
		catch (final JwtException e) {
			recordRejectedPublicKey(rawJwt);
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription(e.getMessage()));
		}

		final Date expiration = claims.getPayload().getExpiration();
		if (expiration == null || expiration.getTime() > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)) {
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription("Invalid expiration."));
		}

		// verify username used to look up key matches signed username to avoid impersonation.
		// TODO why have anything in the claims at all given the header is part of the signature?
		if (!Objects.equals(claims.getHeader().get(HEADER_USERNAME), claims.getPayload().getSubject())) {
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription("Mismatched subject."));
		}

		final String username = (String) claims.getHeader().get(HEADER_USERNAME);
		final ImmutableSet.Builder<Permission> permissions = ImmutableSet.builder();

		final User user = this.users.getUser(username);
		if (user != null) {
			if (user.hasPermission(Permission.EDITTAGS)) permissions.add(Permission.EDITTAGS);
		}

		final Context ctx = Context.current().withValues(
				USERNAME_CONTEXT_KEY, username,
				PERMISSIONS_CONTEXT_KEY, permissions.build());
		return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
	}

	private static <ReqT, RespT> ServerCall.Listener<ReqT> returnStatus(final ServerCall<ReqT, RespT> serverCall, final Status status) {
		serverCall.close(status, new Metadata());
		return new ServerCall.Listener<>() {};
	}

	private void recordRejectedPublicKey(final String rawJwt) {
		try {
			final Jws<Claims> claims = this.insecureParser.parseSignedClaims(rawJwt);
			final String username = (String) claims.getHeader().get(HEADER_USERNAME);
			if (!Objects.equals(username, claims.getPayload().getSubject())) return;
			final PublicJwk<?> publicJwk = getPublicJwkFromHeader(claims.getHeader());
			this.loader.recordRejectedPublicKey(username, publicJwk);
		}
		catch (final UnsupportedJwtException e) {
			// ignore.
		}
	}

	private static PublicJwk<?> getPublicJwkFromHeader(final ProtectedHeader header) {
		final Object raw = header.get(HEADER_JWK);
		if (raw  == null) return null;
		if (!(raw instanceof PublicJwk)) return null;
		return (PublicJwk<?>) raw;
	}

	private static class SelfSignedLocator extends LocatorAdapter<Key> {
		@Override
		public Key locate(final ProtectedHeader header) {
			final PublicJwk<?> jwk = getPublicJwkFromHeader(header);
			if (jwk == null) return null;
			return jwk.toKey();
		}
	}

}

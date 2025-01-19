package com.vaguehope.dlnatoad.rpc.server;

import java.security.Key;
import java.util.Objects;

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

	private static final String BEARER_TYPE = "Bearer";
	private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

	private final JwtLoader loader;
	private final JwtParser secureParser;
	private final JwtParser insecureParser;

	public JwtInterceptor(final JwtLoader loader) {
		this.loader = loader;
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
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription("Authorization token is missing"));
		}
		if (!value.startsWith(BEARER_TYPE)) {
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription("Unknown authorization type"));
		}
		final String rawJwt = value.substring(BEARER_TYPE.length()).trim();

		final Jws<Claims> claims;
		try {
			claims = this.secureParser.parseSignedClaims(rawJwt);
		}
		catch (final JwtException e) {
			recordRejectedPublicKey(rawJwt);
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e));
		}

		// verify username used to look up key matches signed username to avoid impersonation.
		// TODO why have anything in the claims at all given the header is part of the signature?
		if (!Objects.equals(claims.getHeader().get(HEADER_USERNAME), claims.getPayload().getSubject())) {
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription("Mismatched subject."));
		}

		final Context ctx = Context.current().withValue(USERNAME_CONTEXT_KEY, claims.getPayload().getSubject());
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

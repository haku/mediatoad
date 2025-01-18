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
import io.jsonwebtoken.Locator;

public class JwtInterceptor implements ServerInterceptor {

	static final Context.Key<String> USERNAME_CONTEXT_KEY = Context.key("username");

	private static final String BEARER_TYPE = "Bearer";
	private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

	private final JwtParser parser;

	public JwtInterceptor(final Locator<Key> loader) {
		this.parser = Jwts.parser()
				.keyLocator(loader)
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
		final String token = value.substring(BEARER_TYPE.length()).trim();

		final Jws<Claims> claims;
		try {
			claims = this.parser.parseSignedClaims(token);
		}
		catch (final JwtException e) {
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e));
		}

		// verify username used to look up key matches signed username to avoid impersonation.
		// TODO why have anything in the claims at all given the header is part of the signature?
		if (!Objects.equals(claims.getHeader().get("username"), claims.getPayload().getSubject())) {
			return returnStatus(serverCall, Status.UNAUTHENTICATED.withDescription("Mismatched subject."));
		}

		final Context ctx = Context.current().withValue(USERNAME_CONTEXT_KEY, claims.getPayload().getSubject());
		return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
	}

	private static <ReqT, RespT> ServerCall.Listener<ReqT> returnStatus(final ServerCall<ReqT, RespT> serverCall, Status status) {
		serverCall.close(status, new Metadata());
		return new ServerCall.Listener<>() {};
	}
}
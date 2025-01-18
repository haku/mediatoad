package com.vaguehope.dlnatoad.rpc.server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.io.Parser;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;

public class JwtLoader extends LocatorAdapter<Key> {

	private final Map<String, Key> publicKeys;

	public JwtLoader(final File rpcAuthFile) throws IOException {
		final Parser<JwkSet> parser = Jwks.setParser().build();
		final String rawJson = FileUtils.readFileToString(rpcAuthFile, StandardCharsets.UTF_8);
		final JwkSet set = parser.parse(rawJson);
		this.publicKeys = set.getKeys().stream().collect(Collectors.toUnmodifiableMap(Jwk::getId, Jwk::toKey));
	}

	@Override
	public Key locate(final ProtectedHeader header) {
		final String username = (String) header.get("username");
		if (username == null) return null;
		return this.publicKeys.get(username);
	}

}

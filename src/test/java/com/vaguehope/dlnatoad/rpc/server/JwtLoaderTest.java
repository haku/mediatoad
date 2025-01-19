package com.vaguehope.dlnatoad.rpc.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;

public class JwtLoaderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itLooksUpKeysViaUsername() throws Exception {
		final KeyPair pair = Jwts.SIG.ES512.keyPair().build();
		final File f = writeSet(pair);

		final JwtLoader undertest = new JwtLoader(f);

		final String rawJws = Jwts.builder()
				.expiration(Date.from(Instant.now().plusSeconds(30)))
				.subject("alice")
				.header().add("username", "alice").and()
				.signWith(pair.getPrivate(), Jwts.SIG.ES512)
				.compact();

		final JwtParser parser = Jwts.parser()
				.keyLocator(undertest)
				.build();
		final Jws<Claims> jws = parser.parseSignedClaims(rawJws);
		assertEquals("alice", jws.getPayload().getSubject());
		assertEquals("alice", jws.getHeader().get("username"));
	}

	@Test
	public void itAddsPublicKeyToSet() throws Exception {
		final KeyPair existingPair = Jwts.SIG.ES512.keyPair().build();
		final File f = writeSet(existingPair);

		final KeyPair newPair = Jwts.SIG.ES512.keyPair().build();
		final PublicJwk<?> newJwk = Jwks.builder()
				.key(newPair.getPublic())
				.id("bob")
				.build();

		final JwtLoader undertest = new JwtLoader(f);
		undertest.authorisePublicKey("admin-user", "bob", newJwk);

		final Map<String, PublicJwk<?>> actual1 = undertest.getAllowedPublicKeys();
		assertThat(actual1, hasKey("alice"));
		assertThat(actual1, hasKey("bob"));

		final Map<String, PublicJwk<?>> actual2 = new JwtLoader(f).getAllowedPublicKeys();
		assertThat(actual2, hasKey("alice"));
		assertThat(actual2, hasKey("bob"));
	}

	private File writeSet(final KeyPair pair) throws IOException {
		final PublicJwk<?> jwk = Jwks.builder()
				.key(pair.getPublic())
				.id("alice")
				.build();
		final String json = JwtLoader.GSON.toJson(Jwks.set().add(jwk).build());
		final File f = this.tmp.newFile();
		FileUtils.write(f, json, StandardCharsets.UTF_8);
		return f;
	}

}

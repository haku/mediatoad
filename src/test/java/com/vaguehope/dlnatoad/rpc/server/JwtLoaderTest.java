package com.vaguehope.dlnatoad.rpc.server;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.gson.io.GsonSupplierSerializer;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;

public class JwtLoaderTest {

	final Gson gson = new GsonBuilder()
			.registerTypeHierarchyAdapter(io.jsonwebtoken.lang.Supplier.class, GsonSupplierSerializer.INSTANCE)
			.setFormattingStyle(FormattingStyle.PRETTY)
			.create();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itLooksUpKeysViaUsername() throws Exception {
		final KeyPair pair = Jwts.SIG.ES512.keyPair().build();
		final PublicJwk<?> jwk = Jwks.builder()
				.key(pair.getPublic())
				.id("alice")
				.build();
		final JwkSet set = Jwks.set()
				.add(jwk)
				.build();

		final String json = this.gson.toJson(set);

		final File f = this.tmp.newFile();
		FileUtils.write(f, json, StandardCharsets.UTF_8);
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

}

package com.vaguehope.dlnatoad.rpc.server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.gson.io.GsonSupplierSerializer;
import io.jsonwebtoken.lang.Collections;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;

public class JwkLoader extends LocatorAdapter<Key> {

	static final Gson GSON = new GsonBuilder()
			.registerTypeHierarchyAdapter(io.jsonwebtoken.lang.Supplier.class, GsonSupplierSerializer.INSTANCE)
			.setFormattingStyle(FormattingStyle.PRETTY)
			.create();
	private static final Logger LOG = LoggerFactory.getLogger(JwkLoader.class);

	private final File rpcAuthFile;
	private final Map<String, PublicJwk<?>> publicKeys = new ConcurrentHashMap<>();
	private final Cache<String, PublicJwk<?>> recentRejectedPublicKeys = CacheBuilder.newBuilder()
			.maximumSize(10)
			.build();

	public JwkLoader(final File rpcAuthFile) throws IOException {
		this.rpcAuthFile = rpcAuthFile;
		ensureFileExists(rpcAuthFile);
		loadJwkSetFile();
	}

	private static void ensureFileExists(final File file) throws IOException {
		if (!file.exists() && !file.createNewFile()) {
			throw new IOException("Failed to make file: " + file);
		}
		setPrivateKeyFilePermissions(file);
	}

	private static void setPrivateKeyFilePermissions(final File file) throws IOException {
		// there does not appear to be any way to check current permissions, so can only set them to what they should be.
		// only way to clear group and other bits seems to be to clear them all, then just set owner bits.
		if (!file.setReadable(false, false) | !file.setReadable(true, true)) {
			throw new IOException("Failed to set readability of: " + file);
		}
		if (!file.setWritable(false, false) | !file.setWritable(true, true)) {
			throw new IOException("Failed to set writability of: " + file);
		}
	}

	private void loadJwkSetFile() throws IOException {
		final JwkSet set = readJwkSetFile();
		// TODO do this in a way that does not create a moment where no keys are loaded?
		this.publicKeys.clear();
		if (set == null) return;
		for (final Jwk<?> k : set.getKeys()) {
			if (!(k instanceof PublicJwk<?>)) continue;
			this.publicKeys.put(k.getId(), (PublicJwk<?>) k);
		}
	}

	private JwkSet readJwkSetFile() throws IOException {
		final String rawJson = FileUtils.readFileToString(this.rpcAuthFile, StandardCharsets.UTF_8);
		if (StringUtils.isBlank(rawJson)) return null;
		return Jwks.setParser().build().parse(rawJson);
	}

	public void authorisePublicKey(final String callingUsername, final String keyUsername, final PublicJwk<?> publicJwk) throws IOException {
		if (StringUtils.isBlank(keyUsername)) throw new IllegalArgumentException("Missing username.");
		if (!keyUsername.equals(publicJwk.getId()))
			throw new IllegalArgumentException("keyId does not match username: " + keyUsername + " != " + publicJwk.getId());
		if (this.publicKeys.containsKey(keyUsername))
			throw new IllegalArgumentException("username already in RPC auth file: " + keyUsername);

		LOG.warn("{} adding JWT to auth file: {} {}", callingUsername, keyUsername, publicJwk);
		updateAuthFile(callingUsername, keyUsername, s -> s.add(publicJwk));

		this.recentRejectedPublicKeys.invalidate(keyUsername);
	}

	public void revokePublicKey(final String callingUsername, final String keyUsername) throws IOException {
		if (StringUtils.isBlank(keyUsername)) throw new IllegalArgumentException("Missing username.");

		final PublicJwk<?> revokedKey = this.publicKeys.get(keyUsername);

		updateAuthFile(callingUsername, keyUsername, s -> s.removeIf(k -> k.getId().equals(keyUsername)));
		LOG.warn("{} removed user from auth file: {}", callingUsername, keyUsername);

		this.recentRejectedPublicKeys.asMap().putIfAbsent(keyUsername, revokedKey);
	}

	private void updateAuthFile(final String callingUsername, final String keyUsername, final Consumer<Set<Jwk<?>>> updater) throws IOException {
		final Set<Jwk<?>> beforeUpdate = new HashSet<>();
		final JwkSet existingKeys = readJwkSetFile();
		if (existingKeys != null) beforeUpdate.addAll(readJwkSetFile().getKeys());

		final Set<Jwk<?>> afterUpdate = new HashSet<>(beforeUpdate);
		updater.accept(afterUpdate);
		if (afterUpdate.equals(beforeUpdate)) throw new IOException("Updater did not change the keyset.");

		final String json;
		if (afterUpdate.size() > 0) {
			final JwkSet newSet = Jwks.set().add(afterUpdate).build();
			json = GSON.toJson(newSet);
		}
		else {
			json = "";
		}

		// TODO FIXME write this via a tmp file then move over.
		FileUtils.writeStringToFile(this.rpcAuthFile, json, StandardCharsets.UTF_8);
		setPrivateKeyFilePermissions(this.rpcAuthFile);

		loadJwkSetFile();
	}

	@Override
	public Key locate(final ProtectedHeader header) {
		final String username = (String) header.get("username");
		if (username == null) return null;
		final PublicJwk<?> pubKey = this.publicKeys.get(username);
		if (pubKey == null) return null;
		return pubKey.toKey();
	}

	public Map<String, PublicJwk<?>> getAllowedPublicKeys() {
		return Collections.immutable(this.publicKeys);
	}

	public Map<String, PublicJwk<?>> getRecentlyRejectPublicKeys() {
		return Collections.immutable(this.recentRejectedPublicKeys.asMap());
	}

	public void recordRejectedPublicKey(final String username, final PublicJwk<?> publicJwk) {
		this.recentRejectedPublicKeys.asMap().putIfAbsent(username, publicJwk);
	}

}

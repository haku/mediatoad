package mediatoad.dlnaserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jupnp.model.types.UDN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mediatoad.Args;

public class SystemId {

	private static final Logger LOG = LoggerFactory.getLogger(SystemId.class);
	private final UUID id;

	public SystemId(final Args args) throws IOException {
		final File f = args.getIdfile();
		if (f != null && f.exists()) {
			final List<String> lines = FileUtils.readLines(f, StandardCharsets.UTF_8);
			final String raw = lines.size() > 0 ? lines.get(0) : null;
			if (!StringUtils.isEmpty(raw)) {
				this.id = UUID.fromString(raw);
			}
			else {
				this.id = generate(f);
			}
		}
		else {
			this.id = generate(f);
		}

		LOG.info("uniqueSystemIdentifier: {}", this.id);
	}

	public UDN getUsi() {
		return new UDN(this.id);
	}

	private static UUID generate(final File f) throws IOException {
		UUID uuid = maybeGetStableId();
		if (uuid == null) uuid = UUID.randomUUID();

		if (f != null) {
			try (final OutputStream os = new FileOutputStream(f)) {
				IOUtils.write(uuid.toString(), os, StandardCharsets.UTF_8);
			}
		}

		return uuid;
	}

	private static UUID maybeGetStableId() {
		try {
			final UDN udn = UDN.uniqueSystemIdentifier("DLNAtoad-MediaServer");
			return UUID.fromString(udn.getIdentifierString());
		}
		catch (final RuntimeException e) {
			if ("Could not discover first network interface hardware address".equals(e.getMessage())) {
				LOG.warn("Failed to generate stable system identifier, assuming networking is offline and failling back to ramdom.");
				return null;
			}
			throw e;
		}
	}

}

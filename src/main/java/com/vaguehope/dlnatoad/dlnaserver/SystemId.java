package com.vaguehope.dlnatoad.dlnaserver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.model.types.UDN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.Args;

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
		final UDN udn = UDN.uniqueSystemIdentifier("DLNAtoad-MediaServer");
		final UUID i = UUID.fromString(udn.getIdentifierString());

		if (f != null) {
			FileUtils.write(f, i.toString(), StandardCharsets.UTF_8);
		}

		return i;
	}

}

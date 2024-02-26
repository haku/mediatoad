package com.vaguehope.dlnatoad.ffmpeg;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.util.StringHelper;

public class FfprobeParser implements Listener<String> {

	/**
	 * 00:03:57.196000000
	 */
	private static final Pattern DURATION_PATTERN = Pattern.compile("^([0-9]+):([0-9]+):([0-9]+).([0-9]+)$");

	private static final Logger LOG = LoggerFactory.getLogger(FfprobeParser.class);

	private final Set<String> codecs = new LinkedHashSet<>();
	private final Set<String> profiles = new LinkedHashSet<>();
	private long durationMillis = 0;
	private int width = 0;
	private int height = 0;

	@Override
	public void onAnswer (final String line) {
		final String[] parts = StringHelper.splitOnce(line, '=');
		if (parts == null) return;

		if (parts[0].endsWith(".codec_name")) {
			this.codecs.add(StringHelper.removeEndQuotes(parts[1]).toLowerCase(Locale.ENGLISH));
		}
		else if (parts[0].endsWith(".profile")) {
			this.profiles.add(StringHelper.removeEndQuotes(parts[1]).toLowerCase(Locale.ENGLISH));
		}
		else if (parts[0].endsWith(".tags.DURATION")) { // streams.stream.0.tags.DURATION="00:03:57.196000000"
			try {
				final String val = StringHelper.removeEndQuotes(parts[1]);
				if (!"N/A".equalsIgnoreCase(val)) {
					final long millis = parseDurationStringToMillis(val);
					if (millis > this.durationMillis) this.durationMillis = millis;
				}
			}
			catch (final NumberFormatException e) {
				LOG.warn("Failed to parse {}: {}", line, e.toString());
			}
		}
		else if (parts[0].endsWith(".duration")) {
			try {
				final String val = StringHelper.removeEndQuotes(parts[1]);
				if (!"N/A".equalsIgnoreCase(val)) {
					final double seconds = Double.parseDouble(val);
					final long millis = (long) (seconds * 1000d);
					if (millis > this.durationMillis) this.durationMillis = millis;
				}
			}
			catch (final NumberFormatException e) {
				LOG.warn("Failed to parse {}: {}", line, e.toString());
			}
		}
		else if (parts[0].endsWith(".width")) {
			try {
				if (!"N/A".equalsIgnoreCase(parts[1])) {
					this.width = Integer.parseInt(parts[1], 10);
				}
			}
			catch (final NumberFormatException e) {
				LOG.warn("Failed to parse {}: {}", line, e.toString());
			}
		}
		else if (parts[0].endsWith(".height")) {
			try {
				if (!"N/A".equalsIgnoreCase(parts[1])) {
					this.height = Integer.parseInt(parts[1], 10);
				}
			}
			catch (final NumberFormatException e) {
				LOG.warn("Failed to parse {}: {}", line, e.toString());
			}
		}
	}

	private static long parseDurationStringToMillis (final String val) {
		final Matcher m = DURATION_PATTERN.matcher(val);
		if (m.matches()) {
			final int hours = Integer.parseInt(m.group(1));
			final int minutes = Integer.parseInt(m.group(2));
			final int seconds = Integer.parseInt(m.group(3));
			final int totalSeconds = (hours * 60 * 60) + (minutes * 60) + seconds;
			return totalSeconds * 1000L;

		}
		return -1;
	}

	public FfprobeInfo build () {
		return new FfprobeInfo(this.codecs, this.profiles, this.durationMillis, this.width, this.height);
	}

}

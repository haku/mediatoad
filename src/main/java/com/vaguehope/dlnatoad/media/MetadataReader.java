package com.vaguehope.dlnatoad.media;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vaguehope.dlnatoad.fs.MediaFile;

public class MetadataReader {

	public static class Metadata {

		private final String artist;
		private final String album;

		public Metadata (final String artist, final String album) {
			this.artist = artist;
			this.album = album;
		}

		public String getArtist () {
			return this.artist;
		}

		public String getAlbum () {
			return this.album;
		}

		@Override
		public String toString () {
			return String.format("metadata{%s, %s}", this.artist, this.album);
		}

		@Override
		public int hashCode () {
			return Objects.hash(this.artist, this.album);
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == this) return true;
			if (obj == null) return false;
			if (!(obj instanceof Metadata)) return false;
			final Metadata that = (Metadata) obj;
			return Objects.equals(this.artist, that.artist)
					&& Objects.equals(this.album, that.album);
		}

	}

	private static final Pattern ARTIST_ALBUM_FILE_NAME_PATTERN =
			Pattern.compile("^(?<artist>.+?)\\s+?-\\s+?(?<album>.+?)\\s+?-\\s+?.+$");

	private static final Pattern ARTIST_FILE_NAME_PATTERN =
			Pattern.compile("^(?<artist>.+?)\\s+?-\\s+?.+$");

	public static Metadata read (final MediaFile file) {
		if (file == null) return null;

		{
			final Matcher m = ARTIST_ALBUM_FILE_NAME_PATTERN.matcher(file.getName());
			if (m.matches()) return new Metadata(m.group("artist"), m.group("album"));
		}

		{
			final Matcher m = ARTIST_FILE_NAME_PATTERN.matcher(file.getName());
			if (m.matches()) return new Metadata(m.group("artist"), null);
		}

		return null;
	}

}

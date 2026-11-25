package mediatoad.media;

import java.io.File;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetadataReader {

	private static final String EXTENSION = "\\.[A-Za-z0-9]{2,}$";

	private static final Pattern ARTIST_ALBUM_TITLE_FILE_NAME_PATTERN = Pattern.compile(
			"^(?<artist>.+?)\\s+?-\\s+?"
			+ "(?<album>.+?)\\s+?-\\s+?"
			+ "(?<number>[0-9]+\\s+?-\\s+?)?"
			+ "(?<title>.+?)"
			+ EXTENSION);

	private static final Pattern ARTIST_FILE_NAME_PATTERN = Pattern.compile(
			"^(?<artist>.+?)\\s+?-\\s+?"
			+ "(?<title>.+?)"
			+ EXTENSION);

	private static final Pattern ARTIST_ALBUM_DIR_NAME_PATTERN = Pattern.compile(
			"^(?<artist>.+?)\\s+?-\\s+?"
			+ "(?<album>.+?)$");

	private static final Pattern NUMBER_TITLE_FILE_NAME_PATTERN = Pattern.compile(
			"^(?<number>[0-9]+\\s+?-\\s+?)"
			+ "(?<title>.+?)"
			+ EXTENSION);

	private static final Pattern TITLE_FILE_NAME_PATTERN = Pattern.compile(
			"^(?<title>.+?)"
			+ EXTENSION);

	private static final Pattern NOTE_PATTERN = Pattern.compile(
			"(\\s+\\[[^\\]]+\\])");

	public static Metadata read (final File file) {
		if (file == null) return null;

		final String fileName = file.getName();
		final String dirName = file.getParentFile().getName();

		// Artist - Album/## - Title.ext
		{
			final Matcher d = ARTIST_ALBUM_DIR_NAME_PATTERN.matcher(dirName);
			final Matcher f = NUMBER_TITLE_FILE_NAME_PATTERN.matcher(fileName);
			if (d.matches() && f.matches()) return makeMetadata(d.group("artist"), d.group("album"), f.group("title"), Confidence.OK);
		}

		// Album/## - Title.ext
		{
			final Matcher f = NUMBER_TITLE_FILE_NAME_PATTERN.matcher(fileName);
			if (f.matches()) return makeMetadata(null, dirName, f.group("title"), Confidence.OK);
		}

		// Artist - Album - ## - Title.ext
		// Artist - Album - Title.ext
		{
			final Matcher f = ARTIST_ALBUM_TITLE_FILE_NAME_PATTERN.matcher(fileName);
			if (f.matches()) return makeMetadata(f.group("artist"), f.group("album"), f.group("title"), Confidence.OK);
		}

		// Artist - Album/Title.ext
		{
			final Matcher d = ARTIST_ALBUM_DIR_NAME_PATTERN.matcher(dirName);
			final Matcher f = TITLE_FILE_NAME_PATTERN.matcher(fileName);
			if (d.matches() && f.matches()) return makeMetadata(d.group("artist"), d.group("album"), f.group("title"), Confidence.OK);
		}

		// Artist - Title.ext
		{
			final Matcher f = ARTIST_FILE_NAME_PATTERN.matcher(fileName);
			if (f.matches()) return makeMetadata(f.group("artist"), null, f.group("title"), Confidence.OK);
		}

		// Artist/Title.ext
		// this one is a big guess since basically everything will match.
		{
			final Matcher f = TITLE_FILE_NAME_PATTERN.matcher(fileName);
			if (f.matches()) return makeMetadata(dirName, null, f.group("title"), Confidence.GUESS);
		}

		return null;
	}

	private static String removeNotes(final String title) {
		return NOTE_PATTERN.matcher(title).replaceAll("");
	}

	private static Metadata makeMetadata(final String artist, final String album, final String title, final Confidence confidence) {
		return new Metadata(artist, album, removeNotes(title), confidence);
	}

	public enum Confidence {
		OK,
		GUESS,
	}

	public static class Metadata {

		private final String artist;
		private final String album;
		private final String title;
		private final Confidence confidence;

		public Metadata (final String artist, final String album, final String title, final Confidence confidence) {
			if (title == null) throw new IllegalArgumentException("title is required.");
			if (confidence == null) throw new IllegalArgumentException("confidence is required.");

			this.artist = artist;
			this.album = album;
			this.title = title;
			this.confidence = confidence;
		}

		/**
		 * May be null.
		 */
		public String getArtist () {
			return this.artist;
		}

		/**
		 * May be null.
		 */
		public String getAlbum () {
			return this.album;
		}

		/**
		 * Not null.
		 */
		public String getTitle() {
			return this.title;
		}

		public boolean hasConfidence(final Confidence wantedConfidence) {
			return this.confidence.ordinal() <= wantedConfidence.ordinal();
		}

		@Override
		public String toString () {
			return String.format("metadata{%s, %s, %s, %s}", this.artist, this.album, this.title, this.confidence);
		}

		@Override
		public int hashCode () {
			return Objects.hash(this.artist, this.album, this.title, this.confidence);
		}

		@Override
		public boolean equals (final Object obj) {
			if (obj == this) return true;
			if (obj == null) return false;
			if (!(obj instanceof Metadata)) return false;
			final Metadata that = (Metadata) obj;
			return Objects.equals(this.artist, that.artist)
					&& Objects.equals(this.album, that.album)
					&& Objects.equals(this.title, that.title)
					&& Objects.equals(this.confidence, that.confidence);
		}

		protected Metadata withTitle(final String newTitle) {
			return new Metadata(this.artist, this.album, newTitle, this.confidence);
		}

		protected Metadata withConfidence(final Confidence newConfidence) {
			return new Metadata(this.artist, this.album, this.title, newConfidence);
		}

	}

}

package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.vaguehope.dlnatoad.util.InputStremWrapper;

public abstract class MediaFile {

	protected final File file;

	private MediaFile(final File file) {
		this.file = file;
	}

	public static MediaFile forRegular(final File file) {
		return new PlainMediaFile(file);
	}

	public static MediaFile forZip(final File zipFile, final ZipEntry e) {
		return new ZipMediaFile(zipFile, e);
	}

	public abstract String name() throws IOException;
	public abstract boolean exists() throws IOException;
	public abstract long length() throws IOException;
	public abstract long modified() throws IOException;

	public abstract InputStream open() throws IOException;

	private static class PlainMediaFile extends MediaFile {

		public PlainMediaFile(final File file) {
			super(file);
		}

		@Override
		public String toString() {
			return String.format("PlainFile{%s, %s}", this.file.getAbsolutePath());
		}

		@Override
		public String name() throws IOException {
			return this.file.getName();
		}

		@Override
		public boolean exists() throws IOException {
			return this.file.exists();
		}

		@Override
		public long length() throws IOException {
			return this.file.length();
		}

		@Override
		public long modified() throws IOException {
			return this.file.lastModified();
		}

		@Override
		public InputStream open() throws FileNotFoundException {
			return new FileInputStream(this.file);
		}

	}

	private static class ZipMediaFile extends MediaFile {

		private final String subPath;

		public ZipMediaFile(final File file, final ZipEntry e) {
			super(file);
			this.subPath = e.getName();
			// TODO read entry details here?
		}

		@Override
		public String toString() {
			return String.format("ZipFile{%s, %s}", this.file.getAbsolutePath(), this.subPath);
		}

		@Override
		public String name() throws IOException {
			// TODO should this return just the name after the '/' ?
			return this.subPath;
		}

		// BIG TODO: cache zipfile entry / read this in constructor.

		@Override
		public boolean exists() throws IOException {
			try (final ZipFile zf = new ZipFile(this.file)) {
				return zf.getEntry(this.subPath) != null;
			}
		}

		@Override
		public long length() throws IOException {
			try (final ZipFile zf = new ZipFile(this.file)) {
				return zf.getEntry(this.subPath).getSize();
			}
		}

		@Override
		public long modified() throws IOException {
			try (final ZipFile zf = new ZipFile(this.file)) {
				return zf.getEntry(this.subPath).getLastModifiedTime().toMillis();
			}
		}

		@SuppressWarnings("resource")
		@Override
		public InputStream open() throws IOException {
			final ZipFile zf = new ZipFile(this.file);
			final InputStream inputStream;
			try {
				final ZipEntry entry = zf.getEntry(this.subPath);
				if (entry == null) {
					throw new FileNotFoundException("Entry in zip not found: " + this.subPath + " in " + this.file.getAbsolutePath());
				}
				inputStream = zf.getInputStream(entry);
			}
			catch (final Exception e) {
				zf.close();
				throw e;
			}

			return new InputStremWrapper(inputStream) {
				@Override
				public void close() throws IOException {
					super.close();
					zf.close();
				}
			};
		}

	}

}

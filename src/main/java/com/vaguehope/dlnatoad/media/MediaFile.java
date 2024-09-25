package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;

import com.vaguehope.dlnatoad.util.InputStremWrapper;

public abstract class MediaFile {

	protected final File file;

	private MediaFile(final File file) {
		this.file = file;
	}

	public static MediaFile forRegular(final File file) {
		return new PlainMediaFile(file);
	}

	public static MediaFile forZip(final File zipFile, final ZipEntry entry) throws IOException {
		return new ZipMediaFile(zipFile, entry.getName(), true, entry.getSize(), entry.getLastModifiedTime().toMillis());
	}

	public static MediaFile forZip(final File zipFile, final String subPath) throws IOException {
		if (zipFile.exists()) {
			try (final ZipFile zf = new ZipFile(zipFile)) {
				final ZipEntry entry = zf.getEntry(subPath);
				if (entry != null) {
					return forZip(zipFile, entry);
				}
			}
		}
		return new ZipMediaFile(zipFile, subPath, false, -1, -1);
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
		private final String name;
		private final boolean exists;
		private final long length;
		private final long modified;

		public ZipMediaFile(final File file, final String subpath, final boolean exists, final long length, final long modified) throws IOException {
			super(file);
			this.subPath = subpath;
			this.name = StringUtils.defaultIfEmpty(StringUtils.substringAfterLast(this.subPath, "/"), this.subPath);
			this.exists = exists;
			this.length = length;
			this.modified = modified;
		}

		@Override
		public String toString() {
			return String.format("ZipFile{%s, %s}", this.file.getAbsolutePath(), this.subPath);
		}

		@Override
		public String name() throws IOException {
			return this.name;
		}

		@Override
		public boolean exists() throws IOException {
			return this.exists;
		}

		@Override
		public long length() throws IOException {
			if (!this.exists) throw new UnsupportedOperationException();
			return this.length;
		}

		@Override
		public long modified() throws IOException {
			if (!this.exists) throw new UnsupportedOperationException();
			return this.modified;
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

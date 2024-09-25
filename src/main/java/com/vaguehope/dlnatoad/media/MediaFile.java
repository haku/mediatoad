package com.vaguehope.dlnatoad.media;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.resource.Resource;

import com.vaguehope.dlnatoad.util.HashHelper;
import com.vaguehope.dlnatoad.util.HashHelper.Md5AndSha1;
import com.vaguehope.dlnatoad.util.InputStremWrapper;

public abstract class MediaFile {

	protected final File file;

	public MediaFile(final File file) {
		this.file = file;
	}

	public static MediaFile fromPath(final String path) {
		// TODO
		// this needs to be able to expand to any type, just based on what is in the string.
		// needs to match up with what getAbsolutePath() returns.

		throw new UnsupportedOperationException();
	}

	public static MediaFile forFile(final File file) {
		if (file.isDirectory()) return new PlainMediaFile(file);

		final MediaFormat format = MediaFormat.identify(file);
		if (format != null && format.getContentGroup() == ContentGroup.ARCHIVE) {
			switch (format) {
			case ZIP:
				return new ZipDir(file);
			default:
				throw new IllegalArgumentException("Unsupported archive type: " + format);
			}
		}

		return new PlainMediaFile(file);
	}

	public static List<MediaFile> expandZip(final MediaFile zipFile) throws IOException {
		final List<MediaFile> ret = new ArrayList<>();
		try (final ZipInputStream zi = new ZipInputStream(zipFile.open())) {
			ZipEntry e;
			while ((e = zi.getNextEntry()) != null) {
				zi.closeEntry();  // if this is not called then the entry is not populated.
				ret.add(MediaFile.forZipEntry(zipFile, e));
			}
		}
		return ret;
	}

	public static MediaFile forZipEntry(final MediaFile file, final ZipEntry entry) throws IOException {
		return new ZipMediaFile(file.file, entry.getName(), true, entry.getSize(), entry.getLastModifiedTime().toMillis());
	}

	public static MediaFile forZipEntry(final MediaFile file, final String subPath) throws IOException {
		if (file.exists()) {
			try (final ZipFile zf = new ZipFile(file.file)) {
				final ZipEntry entry = zf.getEntry(subPath);
				if (entry != null) {
					return forZipEntry(file, entry);
				}
			}
		}
		return new ZipMediaFile(file.file, subPath, false, -1, -1);
	}

	public abstract String getName();
	public abstract String getAbsolutePath();  // TODO i think this is only ever used for DB entries?  TODO check this!
	public abstract boolean exists();
	public abstract boolean isFile();
	public abstract boolean isDirectory();
	public abstract MediaFile getParentFile();
	public abstract String[] list(FilenameFilter filter);
	public abstract MediaFile containedFile(String name) throws IOException;

	public abstract long length();
	public abstract long lastModified();
	public abstract Md5AndSha1 generateMd5AndSha1() throws IOException;

	public abstract InputStream open() throws IOException;
	public abstract void copyTo(OutputStream out) throws IOException;
	public abstract Resource toResource() throws IOException;

	private static class PlainMediaFile extends MediaFile {

		public PlainMediaFile(final File file) {
			super(file);
		}

		@Override
		public String toString() {
			return String.format("PlainFile{%s, %s}", this.file.getAbsolutePath());
		}

		@Override
		public String getName() {
			return this.file.getName();
		}

		@Override
		public String getAbsolutePath() {
			return this.file.getAbsolutePath();
		}

		@Override
		public boolean exists() {
			return this.file.exists();
		}

		@Override
		public boolean isFile() {
			return this.file.isFile();
		}

		@Override
		public boolean isDirectory() {
			return this.file.isDirectory();
		}

		@Override
		public MediaFile getParentFile() {
			return new PlainMediaFile(this.file.getParentFile());
		}

		@Override
		public String[] list(final FilenameFilter filter) {
			return this.file.list(filter);
		}

		@Override
		public MediaFile containedFile(final String name) {
			return new PlainMediaFile(new File(this.file, name));
		}

		@Override
		public long length() {
			return this.file.length();
		}

		@Override
		public long lastModified() {
			return this.file.lastModified();
		}

		@Override
		public Md5AndSha1 generateMd5AndSha1() throws IOException {
			return HashHelper.generateMd5AndSha1(this.file);
		}

		@Override
		public InputStream open() throws FileNotFoundException {
			return new FileInputStream(this.file);
		}

		@Override
		public void copyTo(final OutputStream out) throws IOException {
			FileUtils.copyFile(this.file, out);
		}

		@Override
		public Resource toResource() throws IOException {
			return Resource.newResource(this.file);
		}

	}

	/**
	 * represents showing the zip file as a ContentNode.
	 * isDirectory() == true.
	 */
	private static class ZipDir extends MediaFile {

		public ZipDir(final File file) {
			super(file);
		}

		@Override
		public String toString() {
			return String.format("ZipDir{%s, %s}", this.file.getAbsolutePath());
		}

		@Override
		public String getName() {
			return this.file.getName();
		}

		@Override
		public String getAbsolutePath() {
			return this.file.getAbsolutePath();
		}

		@Override
		public boolean exists() {
			return this.file.exists();
		}

		@Override
		public boolean isFile() {
			return false;
		}

		@Override
		public boolean isDirectory() {
			return true;
		}

		@Override
		public MediaFile getParentFile() {
			return new PlainMediaFile(this.file.getParentFile());
		}

		@Override
		public String[] list(final FilenameFilter filter) {
			throw new UnsupportedOperationException();
		}

		@Override
		public MediaFile containedFile(final String name) throws IOException {
			return forZipEntry(this, name);
		}

		@Override
		public long length() {
			throw new UnsupportedOperationException();
		}

		@Override
		public long lastModified() {
			return this.file.lastModified();
		}

		@Override
		public Md5AndSha1 generateMd5AndSha1() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public InputStream open() throws FileNotFoundException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void copyTo(final OutputStream out) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Resource toResource() throws IOException {
			throw new UnsupportedOperationException();
		}

	}

	private static class ZipMediaFile extends MediaFile {

		private final String subPath;
		private final String name;
		private final boolean exists;
		private final long length;
		private final long modified;

		public ZipMediaFile(final File rawFile, final String subpath, final boolean exists, final long length, final long modified) throws IOException {
			super(rawFile);
			this.subPath = subpath;
			this.name = this.subPath;
			this.exists = exists;
			this.length = length;
			this.modified = modified;
		}

		@Override
		public String toString() {
			return String.format("ZipMediaFile{%s, %s}", this.file.getAbsolutePath(), this.subPath);
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public String getAbsolutePath() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean exists() {
			return this.exists;
		}

		@Override
		public boolean isFile() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isDirectory() {
			throw new UnsupportedOperationException();
		}

		@Override
		public MediaFile getParentFile() {
			return new ZipDir(this.file);
		}

		@Override
		public String[] list(final FilenameFilter filter) {
			throw new UnsupportedOperationException();
		}

		@Override
		public MediaFile containedFile(final String fileName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long length() {
			if (!this.exists) throw new UnsupportedOperationException();
			return this.length;
		}

		@Override
		public long lastModified() {
			if (!this.exists) throw new UnsupportedOperationException();
			return this.modified;
		}

		@Override
		public Md5AndSha1 generateMd5AndSha1() throws IOException {
			throw new UnsupportedOperationException();
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

		@Override
		public void copyTo(final OutputStream out) throws IOException {
			try (final InputStream in = open()) {
				IOUtils.copy(in, out);
			}
		}

		@Override
		public Resource toResource() throws IOException {
			throw new UnsupportedOperationException();
		}

	}

}

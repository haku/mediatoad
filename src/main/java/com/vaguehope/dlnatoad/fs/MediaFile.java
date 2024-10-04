package com.vaguehope.dlnatoad.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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

		// TODO check for feature flag here in args --expand-archives or something like that.
		if (isZip(file)) {
			return new ZipDir(file);
		}

		return new PlainMediaFile(file);
	}

	static MediaFile forZipEntry(final File zip, final ZipEntry entry) throws IOException {
		return new ZipMediaFile(zip, entry.getName(), true, entry.getSize(), entry.getLastModifiedTime().toMillis());
	}

	static MediaFile forZipEntry(final File zip, final String subPath) throws IOException {
		if (zip.exists()) {
			try (final ZipFile zf = new ZipFile(zip)) {
				final ZipEntry entry = zf.getEntry(subPath);
				if (entry != null) {
					return forZipEntry(zip, entry);
				}
			}
		}
		return new ZipMediaFile(zip, subPath, false, -1, -1);
	}

	static boolean isZip(final File file) {
		return isZip(file.getName());
	}

	static boolean isZip(final MediaFile file) {
		return isZip(file.getName());
	}

	static boolean isZip(final String name) {
		return "zip".equals(FilenameUtils.getExtension(name).toLowerCase());
	}

	public abstract String getName();
	public abstract String getAbsolutePath();  // TODO i think this is only ever used for DB entries?  TODO check this!
	public abstract boolean exists();
	public abstract boolean isFile();
	public abstract boolean isDirectory();
	public abstract MediaFile getParentFile();
	public abstract String[] list(FilenameFilter filter);
	public abstract List<MediaFile> files(FilenameFilter filter) throws IOException;
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
			return String.format("PlainFile{%s}", this.file.getAbsolutePath());
		}

		@Override
		public int hashCode() {
			return this.file.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == this) return true;
			if (obj == null) return false;
			if (!(obj instanceof PlainMediaFile)) return false;
			final PlainMediaFile that = (PlainMediaFile) obj;
			return Objects.equals(this.file, that.file);
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
		public List<MediaFile> files(final FilenameFilter filter) throws IOException {
			if (!isDirectory()) throw new UnsupportedOperationException("Not a directory: " + getAbsolutePath());
			final File[] files = this.file.listFiles(filter);
			if (files == null) throw new IOException("Failed to list files in: " + getAbsolutePath());

			final List<MediaFile> ret = new ArrayList<>();
			for (final File f : files) {
				ret.add(MediaFile.forFile(f));
			}
			return ret;
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
			return String.format("ZipDir{%s}", this.file.getAbsolutePath());
		}

		@Override
		public int hashCode() {
			return this.file.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == this) return true;
			if (obj == null) return false;
			if (!(obj instanceof ZipDir)) return false;
			final ZipDir that = (ZipDir) obj;
			return Objects.equals(this.file, that.file);
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
		public List<MediaFile> files(FilenameFilter filter) throws IOException {
			final List<MediaFile> ret = new ArrayList<>();
			try (final ZipInputStream zi = new ZipInputStream(new FileInputStream(this.file))) {
				ZipEntry e;
				while ((e = zi.getNextEntry()) != null) {
					zi.closeEntry();  // if this is not called then the entry is not populated.
					ret.add(MediaFile.forZipEntry(this.file, e));
				}
			}
			return ret;
		}

		@Override
		public MediaFile containedFile(final String name) throws IOException {
			return forZipEntry(this.file, name);
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
		public List<MediaFile> files(FilenameFilter filter) throws IOException {
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

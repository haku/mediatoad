package com.vaguehope.dlnatoad.db;

import java.math.BigInteger;

import com.vaguehope.dlnatoad.media.MediaFile;
import com.vaguehope.dlnatoad.media.MediaIdCallback;

class FileTask {

	public enum Action {
		ID,
		GONE
	}

	private final Action action;
	private final MediaFile file;
	private final BigInteger auth;
	private final MediaIdCallback callback;
	private final FileData newFileData;
	private final Runnable genericCallback;

	public FileTask(final MediaFile file, final BigInteger auth, final MediaIdCallback callback) {
		if (file == null) throw new IllegalArgumentException("file can not be null.");
		if (auth == null) throw new IllegalArgumentException("auth can not be null, use 0 instead.");
		if (callback == null) throw new IllegalArgumentException("callback can not be null.");
		this.action = Action.ID;
		this.file = file;
		this.auth = auth;
		this.callback = callback;
		this.newFileData = null;
		this.genericCallback = null;
	}

	public FileTask(final MediaFile file) {
		this.action = Action.GONE;
		this.file = file;
		this.auth = null;
		this.callback = null;
		this.newFileData = null;
		this.genericCallback = null;
	}

	public FileTask(final Runnable callback) {
		this.action = null;
		this.file = null;
		this.auth = null;
		this.callback = null;
		this.newFileData = null;
		this.genericCallback = callback;
	}

	private FileTask(final Action action, final MediaFile file, final BigInteger auth, final MediaIdCallback callback, final FileData newFileData, final Runnable genericCallback) {
		this.action = action;
		this.file = file;
		this.auth = auth;
		this.callback = callback;
		this.newFileData = newFileData;
		this.genericCallback = genericCallback;
	}

	public Action getAction() {
		return this.action;
	}

	public MediaFile getFile() {
		return this.file;
	}

	public BigInteger getAuth() {
		return this.auth;
	}

	public MediaIdCallback getCallback() {
		return this.callback;
	}

	public Runnable getGenericCallback() {
		return this.genericCallback;
	}

	public FileData getNewFileData() {
		return this.newFileData;
	}

	public FileTask withNewFileData(final FileData data) {
		return new FileTask(this.action, this.file, this.auth, this.callback, data, this.genericCallback);
	}

	@Override
	public String toString() {
		return String.format("FileTask{%s, %s, %s, %s, %s}", this.action, this.file, this.auth, this.callback, this.genericCallback);
	}

}

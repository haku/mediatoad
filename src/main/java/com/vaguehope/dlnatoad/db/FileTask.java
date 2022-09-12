package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.math.BigInteger;

import com.vaguehope.dlnatoad.media.MediaIdCallback;

class FileTask {

	public enum Action {
		ID,
		GONE
	}

	private final Action action;
	private final File file;
	private final BigInteger auth;
	private final MediaIdCallback callback;
	private final Runnable genericCallback;

	public FileTask(final File file, final BigInteger auth, final MediaIdCallback callback) {
		if (file == null) throw new IllegalArgumentException("file can not be null.");
		if (auth == null) throw new IllegalArgumentException("auth can not be null, use 0 instead.");
		if (callback == null) throw new IllegalArgumentException("callback can not be null.");
		this.action = Action.ID;
		this.file = file;
		this.auth = auth;
		this.callback = callback;
		this.genericCallback = null;
	}

	public FileTask(final File file) {
		this.action = Action.GONE;
		this.file = file;
		this.auth = null;
		this.callback = null;
		this.genericCallback = null;
	}

	public FileTask(final Runnable callback) {
		this.action = null;
		this.file = null;
		this.auth = null;
		this.callback = null;
		this.genericCallback = callback;
	}

	public Action getAction() {
		return this.action;
	}

	public File getFile() {
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

	@Override
	public String toString() {
		return String.format("FileTask{%s, %s, %s, %s, %s}", this.action, this.file, this.auth, this.callback, this.genericCallback);
	}

}

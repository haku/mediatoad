package com.vaguehope.dlnatoad.ffmpeg;

public interface Listener<T> {
	void onAnswer (T answer);
}

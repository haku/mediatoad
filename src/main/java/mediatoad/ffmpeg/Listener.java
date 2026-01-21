package mediatoad.ffmpeg;

public interface Listener<T> {
	void onAnswer (T answer);
}

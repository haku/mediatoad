package com.vaguehope.dlnatoad.util;

public interface AsyncCallback<R, E extends Exception> {

	void onResult(R result) throws E;

	void onError(E e);

}

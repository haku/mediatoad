package com.vaguehope.dlnatoad.util;

public interface ExConsumer<I, E extends Exception> {

	void accept(I input) throws E;

}

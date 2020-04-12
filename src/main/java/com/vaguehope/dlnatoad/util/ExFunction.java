package com.vaguehope.dlnatoad.util;

public interface ExFunction<I, O, E extends Exception> {

	O apply(I input) throws E;

}

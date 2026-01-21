package mediatoad.util;

public interface ExFunction<I, O, E extends Exception> {

	O apply(I input) throws E;

}

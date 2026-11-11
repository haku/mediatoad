package mediatoad.util;

import java.util.Collection;
import java.util.function.Function;

public class CollectionHelper {

	private CollectionHelper () {
		throw new AssertionError();
	}

	public static <I, O> Collection<O> map (final I[] input, final Function<I, O> funciton, final Collection<O> output) {
		for (I i : input) {
			output.add(funciton.apply(i));
		}
		return output;
	}

	public static <I, O> Collection<O> map (final Collection<I> input, final Function<I, O> funciton, final Collection<O> output) {
		for (I i : input) {
			output.add(funciton.apply(i));
		}
		return output;
	}

}

package mediatoad.util;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public class CollectionHelper {

	private CollectionHelper () {
		throw new AssertionError();
	}

	public static <I, O> Collection<O> map (final I[] input, final Function<I, O> funciton, final Collection<O> output) {
		for (final I i : input) {
			output.add(funciton.apply(i));
		}
		return output;
	}

	public static <I, O> Collection<O> map (final Collection<I> input, final Function<I, O> funciton, final Collection<O> output) {
		for (final I i : input) {
			output.add(funciton.apply(i));
		}
		return output;
	}

	/**
	 * Assertion here is that binary search takes the ~same time in a set of any size,
	 * so call contains() the minimal number of times.
	 */
	public static <T> boolean intersects(final Set<T> a, final Set<T> b) {
		final Set<T> ittr;
		final Set<T> cont;
		if (a.size() < b.size()) {
			ittr = a;
			cont = b;
		}
		else {
			ittr = b;
			cont = a;
		}
		for (final T i : ittr) {
			if (cont.contains(i)) return true;
		}
		return false;
	}

}

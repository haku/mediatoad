package com.vaguehope.dlnatoad.util;

public class IndexAnd<T> {

	private final int index;
	private final T item;

	public IndexAnd(final int index, final T item) {
		this.index = index;
		this.item = item;
	}

	public int getIndex() {
		return this.index;
	}

	public T getItem() {
		return this.item;
	}

}

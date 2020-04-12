package com.vaguehope.dlnatoad.dlnaserver;

import java.util.Comparator;

import org.fourthline.cling.support.model.DIDLObject;

enum DIDLObjectOrder implements Comparator<DIDLObject> {
	TITLE {
		@Override
		public int compare (final DIDLObject a, final DIDLObject b) {
			return a.getTitle().compareToIgnoreCase(b.getTitle());
		}
	},
	ID {
		@Override
		public int compare (final DIDLObject a, final DIDLObject b) {
			return a.getId().compareTo(b.getId());
		}
	},
	CREATOR {
		@Override
		public int compare (final DIDLObject a, final DIDLObject b) {
			final String l = a.getCreator() != null ? a.getCreator() : "";
			final String r = b.getCreator() != null ? b.getCreator() : "";
			return l.compareToIgnoreCase(r);
		}
	};

	@Override
	public abstract int compare (DIDLObject a, DIDLObject b);
}
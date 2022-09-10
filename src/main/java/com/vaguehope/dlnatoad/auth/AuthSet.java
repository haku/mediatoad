package com.vaguehope.dlnatoad.auth;

import java.math.BigInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

public class AuthSet {

	// Is this the most efficient choice here?
	// Multimaps.synchronizedSetMultimap(HashMultimap.create()) was also considered.
	private final SetMultimap<String, BigInteger> userToAuthIds = Multimaps.newSetMultimap(new ConcurrentHashMap<>(), ConcurrentHashMap::newKeySet);

	public void add(final AuthList list) {
		if (list == null) return;
		for (final String user : list.usernames()) {
			this.userToAuthIds.put(user, list.getId());
		}
	}

	public Set<BigInteger> authIdsForUser(final String user) {
		if (user == null) return null;
		return this.userToAuthIds.get(user);
	}

}

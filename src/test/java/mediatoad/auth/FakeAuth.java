package mediatoad.auth;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import mediatoad.auth.Users.User;

public class FakeAuth {

	private static final AtomicInteger number = new AtomicInteger();

	private static String makeName() {
		return "user" + number.getAndIncrement();
	}

	public static User user() {
		return new User(makeName(), null, null);
	}

	public static User userWithPermission(final Permission permission) {
		return new User(makeName(), null, Set.of(permission));
	}

	public static AuthList listOfUsers(final User... users) {
		return AuthList.ofNames(Stream.of(users).map(u -> u.getUsername()).toArray(String[]::new));
	}

	public static AuthList listOfUserWithPermission(final User user, final Permission permission) {
		return AuthList.ofNameAndPermission(user.getUsername(), permission);
	}

	public static AuthList listOfEmpty() {
		return AuthList.ofNames();
	}

}

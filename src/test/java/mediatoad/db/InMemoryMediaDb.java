package mediatoad.db;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import mediatoad.db.MediaDb;

public class InMemoryMediaDb extends MediaDb {

	public InMemoryMediaDb() throws SQLException {
		super(dbName());
	}

	private static final AtomicInteger NUMBER = new AtomicInteger(0);

	private static String dbName() {
		return "jdbc:sqlite:file:testdb-" + NUMBER.incrementAndGet() + "?mode=memory&cache=shared";
	}

}

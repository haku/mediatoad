package com.vaguehope.dlnatoad.importer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Test;

public class MetadataDumpTest {

	@Test
	public void itReadsJsonFile() throws Exception {
		@SuppressWarnings("resource")
		MetadataDump actual = MetadataDump.readInputStream(getClass().getResourceAsStream("/test_metadata_dump.json"));
		assertThat(actual.getHashAndTags(), contains(
				new HashAndTags(new BigInteger("d8e244abc5057409cbbfcb8f44c5120ec2e3f64f", 16), Arrays.asList("foo", "bar", "bat")),
				new HashAndTags(new BigInteger("722f710e0f039262c87c50b6af87a9d9132feac4", 16), Arrays.asList("red", "green", "blue"))));
	}

}

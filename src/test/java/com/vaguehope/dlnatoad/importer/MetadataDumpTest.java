package com.vaguehope.dlnatoad.importer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Test;

import com.vaguehope.dlnatoad.importer.HashAndTags.ImportedTag;

public class MetadataDumpTest {

	@Test
	public void itReadsJsonFile() throws Exception {
		@SuppressWarnings("resource")
		MetadataDump actual = MetadataDump.readInputStream(getClass().getResourceAsStream("/test_metadata_dump.json"));
		assertThat(actual.getHashAndTags(), contains(
				new HashAndTags(new BigInteger("d8e244abc5057409cbbfcb8f44c5120ec2e3f64f", 16), Arrays.asList(
						new ImportedTag("foo", 1200000100000L, false),
						new ImportedTag("no mod", 0L, false),
						new ImportedTag("bat", 1200000300000L, true))),
				new HashAndTags(new BigInteger("722f710e0f039262c87c50b6af87a9d9132feac4", 16), Arrays.asList(
						new ImportedTag("red", 1300000100000L, false),
						new ImportedTag("green", 1300000200000L, true),
						new ImportedTag("blue", 1300000300000L, false)))));
	}

}

package com.vaguehope.dlnatoad.dlnaserver;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.Args;

public class SystemIdTest {


	@Rule public TemporaryFolder tmp = new TemporaryFolder();
	private Args args;

	@Before
	public void before() throws Exception {
		this.args = mock(Args.class);
	}

	@Test
	public void itGeneratesWithoutFile() throws Exception {
		final SystemId undertest = new SystemId(this.args);
		assertNotNull(undertest.getUsi());
	}

	@Test
	public void itWritesFile() throws Exception {
		final File f = this.tmp.newFile();
		when(this.args.getIdfile()).thenReturn(f);
		final SystemId undertest = new SystemId(this.args);

		final String expected = undertest.getUsi().getIdentifierString();
		final List<String> actual = FileUtils.readLines(f, StandardCharsets.UTF_8);
		assertEquals(expected, actual.get(0));
	}

	@Test
	public void itReadsFile() throws Exception {
		final String id = UUID.randomUUID().toString();
		final File f = this.tmp.newFile();
		FileUtils.write(f, id, StandardCharsets.UTF_8);
		when(this.args.getIdfile()).thenReturn(f);
		final SystemId undertest = new SystemId(this.args);

		final String actual = undertest.getUsi().getIdentifierString();
		assertEquals(id, actual);
	}

}

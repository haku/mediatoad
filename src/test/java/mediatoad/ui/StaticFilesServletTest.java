package mediatoad.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import mediatoad.TestsWithHttpServer;

public class StaticFilesServletTest extends TestsWithHttpServer {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private StaticFilesServlet undertest;

	@Before
	public void before() throws Exception {
		this.undertest = new StaticFilesServlet(null);
	}

	private void startServer() throws Exception {
		startServer(this.undertest, "/w/*");
	}

	@SuppressWarnings("resource")
	@Test
	public void itServesFile() throws Exception {
		startServer();
		final HttpURLConnection conn = assertRequestResponseCode("/w/test.txt", 0, 200);
		assertEquals("desu\n", IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8));
		final Map<String, List<String>> headers = conn.getHeaderFields();
		final List<String> actualType = headers.get("Content-Type");
		assertThat("Content-Type header present", actualType, not(nullValue()));
		assertEquals("text/plain", actualType.get(0));
	}

	@Test
	public void itServesFileWithDatedPath() throws Exception {
		startServer();
		assertRequestResponseCode("/w/123456/test.txt", 0, 200);
	}

	@Test
	public void itHandlesModifiedFile() throws Exception {
		final File root = this.tmp.newFolder("webroot");
		final String filename = "modified.txt";
		final File file = new File(root, filename);
		final CharSequence text = String.valueOf(System.currentTimeMillis());
		FileUtils.write(file, text, StandardCharsets.UTF_8);

		final long oldTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
		assertTrue(file.setLastModified(oldTime));

		this.undertest = new StaticFilesServlet(root);
		startServer();

		final String path = "/w/" + filename;
		assertRequestResponseCode(path, 0, 200);
		assertRequestResponseCode(path, oldTime, 304);

		assertTrue(file.setLastModified(System.currentTimeMillis()));
		assertRequestResponseCode(path, oldTime, 200);
	}

}

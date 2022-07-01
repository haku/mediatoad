package com.vaguehope.dlnatoad.ffmpeg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

public class ProcessHelper {

	private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

	public static List<String> runAndWait (final String... cmd) throws IOException {
		final List<String> ret = new ArrayList<>();
		runAndWait(cmd, new Listener<String>() {
			@Override
			public void onAnswer (final String line) {
				ret.add(line);
			}
		});
		return ret;
	}

	public static void runAndWait (final String[] cmd, final Listener<String> onLine) throws IOException {
		Exception exception = null;

		final ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		final Process p = pb.start();
		try {
			final BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			try {
				String line;
				while ((line = reader.readLine()) != null) {
					onLine.onAnswer(line);
				}
			}
			finally {
				IOUtils.closeQuietly(reader);
			}
		}
		catch (final Exception e) {
			exception = e;
		}
		finally {
			try {
				if (p.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
					final int result = p.exitValue();
					if (result != 0 && exception == null) {
						throw new IOException("Process failed: cmd=" + Arrays.toString(cmd) + " result=" + result);
					}
				}
				else {
					p.destroyForcibly();
					if (exception == null) {
						if (p.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
							throw new IOException("Process failed: cmd=" + Arrays.toString(cmd) + " result=" + p.exitValue());
						}
						else {
							throw new IOException("Process has not exited and did not shutdown when requested: " + Arrays.toString(cmd));
						}
					}
				}
			}
			catch (InterruptedException e1) {
				if (exception == null) {
					throw new IOException("Interupted waiting for process: " + Arrays.toString(cmd));
				}
			}
		}

		if (exception != null) {
			if (exception instanceof RuntimeException) throw (RuntimeException) exception;
			if (exception instanceof IOException) throw (IOException) exception;
			throw new IOException(exception.toString(), exception);
		}
	}

}

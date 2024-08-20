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

	public static void runAndWait (final String[] cmd, final Listener<String> onLine) throws ProcessException, IOException {
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
						throw new ProcessException(cmd, result);
					}
				}
				else {
					p.destroyForcibly();
					if (exception == null) {
						if (p.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
							throw new ProcessException(cmd, p.exitValue());
						}
						else {
							throw new ProcessException(cmd, FailureReason.PROCESS_TERMINATION_FAILED);
						}
					}
				}
			}
			catch (final InterruptedException e1) {
				if (exception == null) {
					throw new ProcessException(cmd, FailureReason.INTERUPTED_WHILE_WAITING);
				}
			}
		}

		if (exception != null) {
			if (exception instanceof RuntimeException) throw (RuntimeException) exception;
			if (exception instanceof IOException) throw (IOException) exception;
			throw new ProcessException(cmd, exception);
		}
	}

	public enum FailureReason {
		NON_ZERO_EXIT_CODE,
		PROCESS_TERMINATION_FAILED,
		INTERUPTED_WHILE_WAITING,
		EXCEPTION;
	}

	public static class ProcessException extends IOException {

		private static final long serialVersionUID = -7155994946532421402L;

		private final String[] cmd;
		private final FailureReason reason;
		private final int exitCode;

		public ProcessException(final String[] cmd, final FailureReason reason) {
			super("Process failed: cmd=" + Arrays.toString(cmd) + " " + reason);
			this.cmd = cmd;
			this.reason = reason;
			this.exitCode = Integer.MAX_VALUE;
		}

		public ProcessException(final String[] cmd, final int exitCode) {
			super("Process failed: cmd=" + Arrays.toString(cmd) + " result=" + exitCode);
			this.cmd = cmd;
			this.reason = FailureReason.NON_ZERO_EXIT_CODE;
			this.exitCode = exitCode;
		}

		public ProcessException(final String[] cmd, final Exception e) {
			super("Process failed: cmd=" + Arrays.toString(cmd) + ": " + e.toString(), e);
			this.cmd = cmd;
			this.reason = FailureReason.EXCEPTION;
			this.exitCode = Integer.MAX_VALUE;
		}

		public String[] getCmd() {
			return this.cmd;
		}

		public FailureReason getReason() {
			return this.reason;
		}

		public int getExitCode() {
			return this.exitCode;
		}

	}

}

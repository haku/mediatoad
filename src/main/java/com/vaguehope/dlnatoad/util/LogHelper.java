package com.vaguehope.dlnatoad.util;

import java.util.logging.LogManager;

import org.slf4j.bridge.SLF4JBridgeHandler;

public final class LogHelper {

	private LogHelper () {
		throw new AssertionError();
	}

	public static void bridgeJul() {
		LogManager.getLogManager().reset();
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

}

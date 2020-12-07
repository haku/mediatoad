package com.vaguehope.dlnatoad.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public final class NetHelper {

	public static class IfaceAndAddr {
		private final NetworkInterface iface;
		private final InetAddress addr;

		public IfaceAndAddr(final NetworkInterface iface, final InetAddress addr) {
			this.iface = iface;
			this.addr = addr;
		}

		public NetworkInterface getIface() {
			return this.iface;
		}

		public InetAddress getAddr() {
			return this.addr;
		}

		@Override
		public String toString() {
			return String.format("%s (%s)", this.addr, this.iface.getName());
		}
	}

	private NetHelper() {
		throw new AssertionError();
	}

	public static List<IfaceAndAddr> getIpAddresses() throws SocketException {
		final List<IfaceAndAddr> addresses = new ArrayList<>();
		for (final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces
				.hasMoreElements();) {
			final NetworkInterface iface = interfaces.nextElement();
			if (!isUseable(iface)) continue;
			for (final InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
				final InetAddress inetAddr = ifaceAddr.getAddress();
				if (!(inetAddr instanceof Inet4Address)) continue;
				addresses.add(new IfaceAndAddr(iface, inetAddr));
			}
		}
		return addresses;
	}

	private static boolean isUseable(final NetworkInterface iface) throws SocketException {
		if (iface.isLoopback()) return false;

		final String name = iface.getName().toLowerCase(Locale.ROOT);
		if (name == null) return false;
		if (name.startsWith("docker")) return false;
		if (name.startsWith("br-")) return false;

		return true;
	}

}

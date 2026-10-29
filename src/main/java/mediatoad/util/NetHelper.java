package mediatoad.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public final class NetHelper {

	private static final Logger LOG = LoggerFactory.getLogger(NetHelper.IfaceAndAddr.class);

	public static class IfaceAndAddr {
		private final NetworkInterface iface;
		private final InetAddress addr;

		@SuppressFBWarnings(value = "EI_EXPOSE_REP2")
		public IfaceAndAddr(final NetworkInterface iface, final InetAddress addr) {
			this.iface = iface;
			this.addr = addr;
		}

		public NetworkInterface getIface() {
			return this.iface;
		}

		@SuppressFBWarnings(value = "EI_EXPOSE_REP")
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

		final String name = StringUtils.lowerCase(iface.getName(), Locale.ROOT);
		if (name == null) return false;
		if (name.startsWith("docker")) return false;
		if (name.startsWith("br-")) return false;
		if (name.startsWith("tailscale")) return false;

		return true;
	}

	/**
	 * Never returns null.
	 */
	public static InetAddress guessSelfAddress(final List<InetAddress> bindAddresses) throws SocketException {
		if (bindAddresses != null && bindAddresses.size() > 0) {
			final InetAddress ret = bindAddresses.iterator().next();
			LOG.info("Bind addresses: {}, using address for self: {}", bindAddresses, ret);
			return ret;
		}

		final List<IfaceAndAddr> addresses = NetHelper.getIpAddresses();
		if (addresses.size() < 1) throw new SocketException("Failed to guess which interface/address to use for self, try specifying one with --interface");

		final InetAddress ret = addresses.iterator().next().getAddr();
		LOG.info("Available addresses: {}, using address for self: {}", addresses, ret);
		return ret;
	}

}

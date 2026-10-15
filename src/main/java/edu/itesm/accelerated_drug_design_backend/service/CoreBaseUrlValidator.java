package edu.itesm.accelerated_drug_design_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates core instance base URLs against an allowlist of private IPv4 CIDRs (anti-SSRF).
 */
@Component
public class CoreBaseUrlValidator {

	private final List<Ipv4Cidr> allowedCidrs;

	public CoreBaseUrlValidator(
			@Value("${app.core.allowed-cidrs:100.64.0.0/10,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8}")
			String allowedCidrsConfig) {
		this.allowedCidrs = parseCidrs(allowedCidrsConfig);
	}

	public String validateAndNormalize(String rawBaseUrl) {
		if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
			throw new IllegalArgumentException("baseUrl is required");
		}
		URI uri;
		try {
			uri = URI.create(rawBaseUrl.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("baseUrl is not a valid URL");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new IllegalArgumentException("baseUrl must use http or https");
		}
		if (uri.getUserInfo() != null) {
			throw new IllegalArgumentException("baseUrl must not include user info");
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("baseUrl must include a host");
		}
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("baseUrl host could not be resolved: " + host);
		}
		if (addresses.length == 0) {
			throw new IllegalArgumentException("baseUrl host could not be resolved: " + host);
		}
		for (InetAddress address : addresses) {
			if (!isAllowed(address)) {
				throw new IllegalArgumentException(
						"baseUrl host is not in the allowed private CIDR list: " + host);
			}
		}
		String normalized = uri.getScheme() + "://" + host;
		if (uri.getPort() != -1) {
			normalized += ":" + uri.getPort();
		}
		return normalized;
	}

	boolean isAllowed(InetAddress address) {
		if (address instanceof Inet6Address ipv6) {
			return ipv6.isLoopbackAddress();
		}
		if (!(address instanceof Inet4Address ipv4)) {
			return false;
		}
		for (Ipv4Cidr cidr : allowedCidrs) {
			if (cidr.contains(ipv4)) {
				return true;
			}
		}
		return false;
	}

	private static List<Ipv4Cidr> parseCidrs(String config) {
		List<Ipv4Cidr> result = new ArrayList<>();
		if (config == null || config.isBlank()) {
			return result;
		}
		for (String part : config.split(",")) {
			String cidr = part.trim();
			if (!cidr.isEmpty()) {
				result.add(Ipv4Cidr.parse(cidr));
			}
		}
		return result;
	}

	static final class Ipv4Cidr {
		private final int network;
		private final int mask;

		private Ipv4Cidr(int network, int mask) {
			this.network = network;
			this.mask = mask;
		}

		static Ipv4Cidr parse(String cidr) {
			String[] parts = cidr.split("/");
			if (parts.length != 2) {
				throw new IllegalArgumentException("Invalid CIDR: " + cidr);
			}
			InetAddress addr;
			try {
				addr = InetAddress.getByName(parts[0].trim());
			} catch (UnknownHostException e) {
				throw new IllegalArgumentException("Invalid CIDR address: " + cidr);
			}
			if (!(addr instanceof Inet4Address ipv4)) {
				throw new IllegalArgumentException("Only IPv4 CIDRs are supported: " + cidr);
			}
			int prefix;
			try {
				prefix = Integer.parseInt(parts[1].trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid CIDR prefix: " + cidr);
			}
			if (prefix < 0 || prefix > 32) {
				throw new IllegalArgumentException("Invalid CIDR prefix: " + cidr);
			}
			int mask = prefix == 0 ? 0 : 0xFFFFFFFF << (32 - prefix);
			int network = toInt(ipv4) & mask;
			return new Ipv4Cidr(network, mask);
		}

		boolean contains(Inet4Address address) {
			return (toInt(address) & mask) == network;
		}

		private static int toInt(Inet4Address address) {
			byte[] bytes = address.getAddress();
			return ((bytes[0] & 0xFF) << 24)
					| ((bytes[1] & 0xFF) << 16)
					| ((bytes[2] & 0xFF) << 8)
					| (bytes[3] & 0xFF);
		}
	}
}

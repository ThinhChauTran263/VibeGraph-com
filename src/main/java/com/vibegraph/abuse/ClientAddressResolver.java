package com.vibegraph.abuse;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

public class ClientAddressResolver {

    private final AbuseProperties properties;

    public ClientAddressResolver(AbuseProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String remote = canonicalize(request.getRemoteAddr());
        if (!properties.isTrustProxy() || !isTrustedProxy(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        return Arrays.stream(forwarded.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .map(ClientAddressResolver::tryCanonicalize)
                .flatMap(Optional::stream)
                .filter(ClientAddressResolver::isPublicClientAddress)
                .findFirst()
                .orElse(remote);
    }

    private boolean isTrustedProxy(String remote) {
        InetAddress remoteAddress = parse(remote)
                .orElse(null);
        if (remoteAddress == null) {
            return false;
        }
        return properties.getTrustedProxies().stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .anyMatch(entry -> matchesTrustedProxy(entry.trim(), remoteAddress));
    }

    private static boolean matchesTrustedProxy(String trustedProxy, InetAddress remoteAddress) {
        if (trustedProxy.contains("/")) {
            return matchesCidr(trustedProxy, remoteAddress);
        }
        return tryCanonicalize(trustedProxy)
                .flatMap(ClientAddressResolver::parse)
                .map(remoteAddress::equals)
                .orElse(false);
    }

    private static boolean matchesCidr(String cidr, InetAddress remoteAddress) {
        String[] parts = cidr.split("/", 2);
        if (parts.length != 2) {
            return false;
        }
        Optional<InetAddress> network = parse(parts[0].trim());
        if (network.isEmpty() || network.get().getAddress().length != remoteAddress.getAddress().length) {
            return false;
        }
        try {
            int prefixLength = Integer.parseInt(parts[1].trim());
            int bitLength = remoteAddress.getAddress().length * 8;
            if (prefixLength < 0 || prefixLength > bitLength) {
                return false;
            }
            return hasPrefix(remoteAddress.getAddress(), network.get().getAddress(), prefixLength);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean hasPrefix(byte[] address, byte[] network, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != network[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (address[fullBytes] & mask) == (network[fullBytes] & mask);
    }

    private static boolean isPublicClientAddress(String address) {
        return parse(address)
                .filter(candidate -> !candidate.isAnyLocalAddress())
                .filter(candidate -> !candidate.isLoopbackAddress())
                .filter(candidate -> !candidate.isLinkLocalAddress())
                .filter(candidate -> !candidate.isSiteLocalAddress())
                .filter(candidate -> !candidate.isMulticastAddress())
                .filter(candidate -> !isCarrierGradeNat(candidate))
                .isPresent();
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    public static String canonicalize(String address) {
        if (address == null || address.isBlank()) {
            return "unknown";
        }
        String candidate = address.trim();
        int scopeSeparator = candidate.indexOf('%');
        if (scopeSeparator > 0 && candidate.indexOf(':') >= 0) {
            candidate = candidate.substring(0, scopeSeparator);
        }
        boolean ipv4Shape = candidate.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        boolean ipv6Shape = candidate.contains(":") && candidate.matches("[0-9a-fA-F:.]+");
        if (!ipv4Shape && !ipv6Shape) {
            throw new IllegalArgumentException("IP address is invalid");
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("IP address is invalid", ex);
        }
    }

    private static Optional<String> tryCanonicalize(String address) {
        try {
            return Optional.of(canonicalize(address));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<InetAddress> parse(String address) {
        try {
            return Optional.of(InetAddress.getByName(address));
        } catch (UnknownHostException | RuntimeException ignored) {
            return Optional.empty();
        }
    }
}

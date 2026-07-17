package com.vibegraph.abuse;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
        String first = forwarded.split(",", 2)[0].trim();
        return first.isBlank() ? remote : canonicalize(first);
    }

    private boolean isTrustedProxy(String remote) {
        return properties.getTrustedProxies().stream()
                .map(ClientAddressResolver::canonicalize)
                .anyMatch(remote::equals);
    }

    public static String canonicalize(String address) {
        if (address == null || address.isBlank()) {
            return "unknown";
        }
        String candidate = address.trim();
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
}

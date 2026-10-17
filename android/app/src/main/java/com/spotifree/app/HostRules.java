package com.spotifree.app;

import java.util.Set;

final class HostRules {
    private HostRules() {}

    static boolean matches(String hostname, Set<String> rules) {
        if (hostname == null) return false;
        String host = hostname.toLowerCase(java.util.Locale.ROOT);
        for (String rule : rules) {
            if (host.equals(rule) || host.endsWith("." + rule)) return true;
        }
        return false;
    }
}

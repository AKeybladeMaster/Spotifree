package com.spotifree.v2;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class HostRules {
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private final Set<String> domains = new HashSet<String>();
    private final Set<String> addresses = new HashSet<String>();

    private HostRules() { }

    static HostRules empty() { return new HostRules(); }

    static HostRules parse(BufferedReader reader) throws IOException {
        HostRules rules = new HostRules();
        String line;
        while ((line = reader.readLine()) != null) rules.addLine(line);
        return rules;
    }

    static HostRules parseLines(Iterable<String> lines) {
        HostRules rules = new HostRules();
        for (String line : lines) rules.addLine(line);
        return rules;
    }

    boolean blocksHost(String rawHost) {
        String host = normalizeHost(rawHost);
        if (host.isEmpty()) return false;
        if (isIpAddress(host)) return addresses.contains(normalizeAddress(host));
        for (String domain : domains)
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        return false;
    }

    // Hosts-format rows start with a sink IP; only the following names are block targets.
    private void addLine(String rawLine) {
        if (rawLine == null) return;
        int comment = rawLine.indexOf('#');
        String line = (comment >= 0 ? rawLine.substring(0, comment) : rawLine).trim();
        if (line.isEmpty()) return;
        String[] fields = line.split("\\s+");
        int firstTarget = fields.length > 1 && isIpAddress(normalizeHost(fields[0])) ? 1 : 0;
        for (int i = firstTarget; i < fields.length; i++) {
            String host = normalizeHost(fields[i]);
            if (isIpAddress(host)) addresses.add(normalizeAddress(host));
            else if (isDomainName(host)) domains.add(host);
        }
    }

    private static String normalizeHost(String value) {
        if (value == null) return "";
        String host = value.trim();
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.toLowerCase(Locale.US);
    }

    private static String normalizeAddress(String host) {
        if (host.indexOf(':') < 0) {
            String[] octets = host.split("\\.", -1);
            StringBuilder normalized = new StringBuilder();
            for (String octet : octets) {
                if (normalized.length() > 0) normalized.append('.');
                normalized.append(Integer.parseInt(octet));
            }
            return normalized.toString();
        }
        try { return InetAddress.getByName(host).getHostAddress().toLowerCase(Locale.US); }
        catch (UnknownHostException ignored) { return host; }
    }
    private static boolean isDomainName(String host) {
        if (host.isEmpty() || host.length() > 253) return false;
        String[] labels = host.split("\\.", -1);
        for (String label : labels)
            if (label.isEmpty() || label.length() > 63 || !LABEL.matcher(label).matches()) return false;
        return true;
    }

    private static boolean isIpAddress(String host) {
        if (host.indexOf(':') >= 0) {
            try { return InetAddress.getByName(host) instanceof Inet6Address; }
            catch (UnknownHostException ignored) { return false; }
        }
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) return false;
            for (int i = 0; i < octet.length(); i++)
                if (octet.charAt(i) < '0' || octet.charAt(i) > '9') return false;
            try { if (Integer.parseInt(octet) > 255) return false; }
            catch (NumberFormatException ignored) { return false; }
        }
        return true;
    }
}

package com.spotifree.app;
import java.util.Set;
/** Dependency-free smoke test runnable with the system JDK. */
public final class HostRulesSmoke {
    public static void main(String[] args) {
        Set<String> rules = Set.of("example.org");
        check(HostRules.matches("example.org", rules));
        check(HostRules.matches("child.example.org", rules));
        check(HostRules.matches("CHILD.EXAMPLE.ORG", rules));
        check(!HostRules.matches("fakeexample.org", rules));
        check(!HostRules.matches("example.org.evil.com", rules));
        check(!HostRules.matches(null, rules));
        System.out.println("HostRules smoke passed (6 checks)");
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Host rule policy violated");
    }
}

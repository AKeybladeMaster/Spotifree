package com.spotifree.app;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Set;

public class HostRulesTest {
    @Test public void subdomainsMatchWithoutFalseSuffixes() {
        Set<String> hosts = Set.of("example.org");
        assertTrue(HostRules.matches("example.org", hosts));
        assertTrue(HostRules.matches("sub.example.org", hosts));
        assertTrue(HostRules.matches("SUB.Example.Org", hosts));
        assertFalse(HostRules.matches("notexample.org", hosts));
        assertFalse(HostRules.matches(null, hosts));
    }
}

package com.spotifree.app;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class AdFilter {
    // Deliberately no UI or settings toggle; developer build-time opt-in only.
    // Never intercept playback, auth or licenses without validating behavior.
    private static final boolean ENABLED = false;
    private final Set<String> hosts;

    AdFilter(Context context) {
        Set<String> loaded = new HashSet<>();
        if (ENABLED) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open("blocked-hosts.txt"),
                                          StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String rule = line.split("#", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
                    if (!rule.isEmpty() && !rule.contains("/") && !rule.contains(" "))
                        loaded.add(rule);
                }
            } catch (Exception ignored) {
                // Filtering is optional; browser operation must not depend on the list.
            }
        }
        hosts = Collections.unmodifiableSet(loaded);
    }

    WebResourceResponse intercept(Uri uri) {
        if (!ENABLED || uri == null || !HostRules.matches(uri.getHost(), hosts))
            return null;
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream(new byte[0]));
    }
}

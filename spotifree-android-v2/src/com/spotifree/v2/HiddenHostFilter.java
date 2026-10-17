package com.spotifree.v2;
import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
/** Developer-only filtering. No toggle or UI; disabled in the shipped build. */
final class HiddenHostFilter {
    private static final boolean ENABLED = false;
    private final Set<String> hosts;
    HiddenHostFilter(Context context) {
        if (!ENABLED) { hosts = Collections.emptySet(); return; }
        Set<String> rules = new HashSet<String>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("blocked-hosts.txt"), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String host = line.split("#", 2)[0].trim().toLowerCase(Locale.US);
                if (!host.isEmpty()) rules.add(host);
            }
            reader.close();
        } catch (Exception ignored) { }
        hosts = Collections.unmodifiableSet(rules);
    }
    WebResourceResponse intercept(Uri uri) {
        if (!ENABLED || uri == null || uri.getHost() == null) return null;
        String host = uri.getHost().toLowerCase(Locale.US);
        for (String rule : hosts) if (host.equals(rule) || host.endsWith("." + rule)) {
            WebResourceResponse response = new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            response.setStatusCodeAndReasonPhrase(204, "No Content");
            return response;
        }
        return null;
    }
}
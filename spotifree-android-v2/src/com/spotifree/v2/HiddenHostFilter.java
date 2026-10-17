package com.spotifree.v2;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

/** Developer-only filtering. No toggle or UI; disabled in the shipped build. */
final class HiddenHostFilter {
    private static final boolean ENABLED = false;
    private final HostRules rules;

    HiddenHostFilter(Context context) {
        HostRules loaded = HostRules.empty();
        if (ENABLED) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open("blocked-hosts.txt"), "UTF-8"))) {
                loaded = HostRules.parse(reader);
            } catch (Exception ignored) { }
        }
        rules = loaded;
    }

    WebResourceResponse intercept(Uri uri) {
        if (uri == null || !rules.blocksHost(uri.getHost())) return null;
        WebResourceResponse response = new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
        response.setStatusCodeAndReasonPhrase(204, "No Content");
        return response;
    }
}

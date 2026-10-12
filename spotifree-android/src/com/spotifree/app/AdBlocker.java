package com.spotifree.app;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AdBlocker {
    private final Set<String> hosts = new HashSet<String>();

    AdBlocker(Context context) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("blocked-hosts.txt"), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String host = line.split("#", 2)[0].trim().toLowerCase(Locale.US);
                if (!host.isEmpty()) hosts.add(host);
            }
            reader.close();
        } catch (Exception ignored) { }
    }

    WebResourceResponse intercept(String url) {
        String host = Uri.parse(url).getHost();
        if (host == null) return null;
        host = host.toLowerCase(Locale.US);
        for (String rule : hosts) {
            if (host.equals(rule) || host.endsWith("." + rule)) {
                WebResourceResponse response = new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                response.setStatusCodeAndReasonPhrase(204, "No Content");
                return response;
            }
        }
        return null;
    }
}

package com.spotifree.v2;

import java.net.URI;
import java.net.URISyntaxException;

/** Grants only protected playback access, only within Spotify's HTTPS player. */
final class ProtectedMediaPolicy {
    static final String PROTECTED_MEDIA_ID = "android.webkit.resource.PROTECTED_MEDIA_ID";

    private ProtectedMediaPolicy() { }

    static String[] allowedResources(String origin, String pageUrl, String[] requested) {
        if (!isPlayerUrl(origin) || !isPlayerUrl(pageUrl) || requested == null)
            return new String[0];
        for (String resource : requested)
            if (PROTECTED_MEDIA_ID.equals(resource)) return new String[]{PROTECTED_MEDIA_ID};
        return new String[0];
    }

    static boolean isPlayerUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                && "open.spotify.com".equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (URISyntaxException ignored) { return false; }
    }
}

package com.spotifree.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.lang.ref.WeakReference;

public final class MainActivity extends Activity {
    private static final String HOME = "https://open.spotify.com/";
    private static WeakReference<MainActivity> current = new WeakReference<>(null);

    private static final String STATE_JS =
        "(function() { try {" +
        "let m = document.querySelector('audio,video');" +
        "let s = navigator.mediaSession; let md = s && s.metadata;" +
        "return JSON.stringify({" +
        "hasMedia:!!((md && md.title) || (m && Number.isFinite(m.duration) && m.duration > 0))," +
        "title:md && md.title || document.title || 'Spotify Web Player'," +
        "artist:md && md.artist || ''," +
        "playing:m ? !m.paused : !!(s && s.playbackState === 'playing')," +
        "position:m && Number.isFinite(m.currentTime) ? Math.round(m.currentTime*1000) : 0," +
        "duration:m && Number.isFinite(m.duration) ? Math.round(m.duration*1000) : 0" +
        "}); } catch(e) { return '{}'; } })()";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private AdFilter filter;
    private boolean destroyed;
    private boolean playbackCleared;
    private String lastState = "";
    private long lastUpdateMs;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (browser != null && isPlayerUrl(browser.getUrl())) {
                browser.evaluateJavascript(STATE_JS, MainActivity.this::consumeState);
            } else {
                clearPlayback();
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        current = new WeakReference<>(this);
        filter = new AdFilter(this);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.rgb(18, 18, 18));
        setContentView(browser); // No custom header, profile picker, or ad toggle.
        configureWebView();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                       != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
        if (state == null || browser.restoreState(state) == null) {
            openIntent(getIntent());
        }
        handler.postDelayed(poll, 1500);
    }

    private void configureWebView() {
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);

        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("https".equals(url.getScheme())) return false;
                if ("mailto".equals(url.getScheme()) || "tel".equals(url.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, url)); }
                    catch (ActivityNotFoundException ignored) { }
                }
                return true; // HTTP, file, content, javascript and intent are disallowed.
            }
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                return filter.intercept(request.getUrl());
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (isPlayerUrl(url)) lastState = "";
                else clearPlayback();
            }
        });
    }

    private static boolean isPlayerUrl(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        return "https".equals(uri.getScheme()) && "open.spotify.com".equals(uri.getHost());
    }

    private void openIntent(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        String dest = uri != null && isPlayerUrl(uri.toString()) ? uri.toString() : HOME;
        browser.loadUrl(dest);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openIntent(intent);
    }

    private void consumeState(String value) {
        if (destroyed || browser == null || !isPlayerUrl(browser.getUrl())) return;
        try {
            Object parsed = new JSONTokener(value).nextValue();
            if (!(parsed instanceof String)) return;
            JSONObject json = new JSONObject((String) parsed);
            if (!json.optBoolean("hasMedia", false)) {
                clearPlayback();
                return;
            }
            playbackCleared = false;
            String title = json.optString("title", "Spotify Web Player");
            String artist = json.optString("artist", "");
            boolean playing = json.optBoolean("playing");
            long position = Math.max(0, json.optLong("position"));
            long duration = Math.max(0, json.optLong("duration"));
            String fingerprint = title + "|" + artist + "|" + playing;
            long now = android.os.SystemClock.elapsedRealtime();
            // Notify on state/track change or at most every ten seconds.
            if (fingerprint.equals(lastState) && now - lastUpdateMs < 10000) return;
            lastState = fingerprint;
            lastUpdateMs = now;
            Intent update = new Intent(this, PlaybackService.class)
                .setAction(PlaybackService.ACTION_STATE)
                .putExtra("title", title)
                .putExtra("artist", artist)
                .putExtra("playing", playing)
                .putExtra("position", position)
                .putExtra("duration", duration);
            try {
                if (playing && Build.VERSION.SDK_INT >= 26) startForegroundService(update);
                else startService(update);
            } catch (RuntimeException ignored) {
                // OS background-start limits may stop notification updates.
            }
        } catch (Exception ignored) {
            // Site changes must never crash the native application.
        }
    }

    private void clearPlayback() {
        if (playbackCleared) return;
        playbackCleared = true;
        lastState = "";
        lastUpdateMs = 0;
        stopService(new Intent(this, PlaybackService.class));
        PlayerWidget.renderAll(this, "Spotifree", false);
    }

    static void control(String action) {
        MainActivity activity = current.get();
        if (activity == null || activity.destroyed) return;
        activity.runOnUiThread(() -> {
            if (activity.browser == null || !isPlayerUrl(activity.browser.getUrl())) return;
            String id;
            switch (action) {
                case "previous": id = "control-button-skip-back"; break;
                case "next": id = "control-button-skip-forward"; break;
                case "play": case "pause": case "toggle":
                    id = "control-button-playpause"; break;
                default: return;
            }
            // action is a closed set of hardcoded strings; never remote arbitrary JS.
            String js = "(function(){var a='" + action + "';" +
                "var m=document.querySelector('audio,video');" +
                "if(m && a==='play' && !m.paused)return;" +
                "if(m && a==='pause' && m.paused)return;" +
                "var b=document.querySelector('[data-testid=\"" + id + "\"]');" +
                "if(b){b.click();return;}" +
                "if(m && (a==='toggle'||a==='play'||a==='pause')){" +
                "if((a==='play')||(a==='toggle'&&m.paused))m.play().catch(function(){});" +
                "else m.pause();}})()";
            activity.browser.evaluateJavascript(js, null);
        });
    }

    private void search() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Search Spotify");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        new AlertDialog.Builder(this)
            .setTitle("Search")
            .setView(input)
            .setPositiveButton("Search", (dialog, which) -> {
                String term = input.getText().toString().trim();
                if (!term.isEmpty() && term.length() <= 512)
                    browser.loadUrl(HOME + "search/" + Uri.encode(term));
                else if (!term.isEmpty())
                    Toast.makeText(this, "Search query too long", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_K) {
            search();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { control("toggle"); return true; }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) { control("next"); return true; }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { control("previous"); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (browser != null) browser.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacks(poll);
        if (current.get() == this) current.clear();
        if (isFinishing()) {
            stopService(new Intent(this, PlaybackService.class));
        }
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        if (isFinishing()) PlayerWidget.renderAll(this, "Spotifree", false);
        super.onDestroy();
    }
}

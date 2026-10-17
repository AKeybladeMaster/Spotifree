package com.spotifree.v2;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaDrm;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final String HOME = "https://open.spotify.com/";
    private static final long POLL_MS = 1800L;
    private static final int MINIMUM_SPOTIFY_CHROME_VERSION = 87;
    private static volatile WeakReference<MainActivity> active = new WeakReference<MainActivity>(null);
    private static final String STATE_SCRIPT = "(function(){var m=document.querySelector('audio,video'),s=navigator.mediaSession,d=s&&s.metadata;" +
        "var has=!!((d&&d.title)||(m&&(m.currentSrc||(m.readyState>0&&m.duration>0))));" +
        "return {hasMedia:has,title:(d&&d.title)||document.title||'Spotify Web Player',artist:(d&&d.artist)||''," +
        "playing:m?!m.paused:!!(s&&s.playbackState==='playing'),position:m&&isFinite(m.currentTime)?Math.round(m.currentTime*1000):0," +
        "duration:m&&isFinite(m.duration)?Math.round(m.duration*1000):0};})()";
    private static final String PLAYBACK_DIAGNOSTICS_SCRIPT = "(async function(){var r={ua:navigator.userAgent,secureContext:isSecureContext," +
        "mediaSource:!!window.MediaSource,emeApi:typeof navigator.requestMediaKeySystemAccess==='function'};" +
        "try{r.uaData=navigator.userAgentData?{mobile:navigator.userAgentData.mobile,platform:navigator.userAgentData.platform," +
        "brands:navigator.userAgentData.brands.map(function(x){return x.brand+'/'+x.version;})}:null;}catch(e){r.uaData='unavailable';}" +
        "var a=document.createElement('audio');r.audioOgg=!!a.canPlayType('audio/ogg; codecs=\"vorbis\"');" +
        "r.audioMp4=!!a.canPlayType('audio/mp4; codecs=\"mp4a.40.2\"');" +
        "if(r.emeApi){try{await navigator.requestMediaKeySystemAccess('com.widevine.alpha',[{initDataTypes:['cenc']," +
        "audioCapabilities:[{contentType:'audio/mp4; codecs=\"mp4a.40.2\"'}]}]);r.widevineEme='available';}" +
        "catch(e){r.widevineEme=e.name+': '+e.message;}}else{r.widevineEme='EME API unavailable';}" +
        "return JSON.stringify(r);})()";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private HiddenHostFilter hiddenFilter;
    private final Runnable pollTask = new Runnable() { @Override public void run() { pollPlayerState(); } };
    private WebView webView;
    private EditText searchBox;
    private TextView status;
    private String pendingControl;
    private String lastMediaKey = "";
    private long lastStateAt;
    private boolean lastHadMedia;
    private boolean polling;
    private int webViewChromeMajor = -1;
    private String webViewProvider = "Unknown";
    private boolean nativeWidevineSupported;
    private String pagePlaybackDiagnostics = "Open Spotify to run browser capability checks.";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18, 18, 18));
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 18));
        buildUi();
        configureWebView();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        openIntent(getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        active = new WeakReference<MainActivity>(this);
        if (webView != null && isSpotifyPlayer(webView.getUrl())) startPolling();
    }

    @Override protected void onPause() { stopPolling(); super.onPause(); }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        root.setFitsSystemWindows(true);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setHintTextColor(Color.LTGRAY);
        searchBox.setHint("Search Spotify");
        searchBox.setTextSize(15);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { performSearch(); return true; }
            return false;
        });
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        addButton(searchRow, "Search", v -> performSearch());
        root.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        addButton(navigation, "Back", v -> { if (webView != null && webView.canGoBack()) webView.goBack(); });
        addButton(navigation, "Forward", v -> { if (webView != null && webView.canGoForward()) webView.goForward(); });
        addButton(navigation, "Home", v -> navigate(HOME));
        addButton(navigation, "Reload", v -> { if (webView != null) webView.reload(); });
        addButton(navigation, "Info", v -> showWebViewDiagnostics());
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout media = new LinearLayout(this);
        media.setGravity(Gravity.CENTER);
        addButton(media, "Previous", v -> dispatchControl("previous"));
        addButton(media, "Play / Pause", v -> dispatchControl("playpause"));
        addButton(media, "Next", v -> dispatchControl("next"));
        root.addView(media, new LinearLayout.LayoutParams(-1, dp(48)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this);
        status.setSingleLine(true);
        status.setTextColor(Color.LTGRAY);
        status.setText("Spotify Web Player");
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));
        setContentView(root);
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setOnClickListener(listener);
        parent.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
    }
    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        hiddenFilter = new HiddenHostFilter(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        String nativeUserAgent = settings.getUserAgentString();
        webViewChromeMajor = getChromeMajorVersion(nativeUserAgent);
        // Present the real Android Chromium engine as mobile Chrome. Keep its actual
        // version and Android identity; remove only Android WebView-specific markers.
        String spotifyUserAgent = asAndroidChromeUserAgent(nativeUserAgent);
        if (!spotifyUserAgent.equals(nativeUserAgent)) settings.setUserAgentString(spotifyUserAgent);
        webViewProvider = getWebViewProviderVersion();
        try {
            nativeWidevineSupported = MediaDrm.isCryptoSchemeSupported(
                UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"));
        } catch (RuntimeException ignored) { nativeWidevineSupported = false; }
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                if (status != null && progress < 100) status.setText("Loading " + progress + "%");
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme)) return false;
                if (scheme != null && !"http".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) { }
                } else if (status != null) status.setText("Secure HTTPS pages only");
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return hiddenFilter.intercept(request.getUrl());
            }
            @Override public void onPageFinished(WebView view, String url) {
                String title = view.getTitle();
                if (webViewChromeMajor > 0 && webViewChromeMajor < MINIMUM_SPOTIFY_CHROME_VERSION)
                    status.setText("Update Chrome / Android System WebView to version 87 or newer.");
                else status.setText(title == null || title.trim().isEmpty() ? "Spotify Web Player" : title);
                view.evaluateJavascript(PLAYBACK_DIAGNOSTICS_SCRIPT, value -> {
                    if (value == null || "null".equals(value)) return;
                    try {
                        Object decoded = new org.json.JSONTokener(value).nextValue();
                        pagePlaybackDiagnostics = decoded instanceof String ? (String) decoded : decoded.toString();
                    } catch (Exception ignored) { pagePlaybackDiagnostics = value; }
                });
                if (isSpotifyPlayer(url)) {
                    if (pendingControl != null) { String action = pendingControl; pendingControl = null; performControl(action); }
                    startPolling();
                } else stopPolling();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && status != null) status.setText("Page load failed");
            }
        });
    }

    private void openIntent(Intent intent) {
        String action = intent == null ? null : intent.getAction();
        String command = commandFromAction(action);
        if (command != null) {
            if (isSpotifyPlayer(webView.getUrl()) && webView.getProgress() >= 100) {
                pendingControl = null;
                performControl(command);
            } else {
                pendingControl = command;
                if (!isSpotifyPlayer(webView.getUrl())) navigate(HOME);
            }
            return;
        }
        Uri data = intent == null ? null : intent.getData();
        if (data != null && "https".equalsIgnoreCase(data.getScheme()) && "open.spotify.com".equalsIgnoreCase(data.getHost()))
            navigate(data.toString());
        else navigate(HOME);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openIntent(intent);
    }

    private String commandFromAction(String action) {
        if (PlaybackService.ACTION_PREVIOUS.equals(action)) return "previous";
        if (PlaybackService.ACTION_NEXT.equals(action)) return "next";
        if (PlaybackService.ACTION_PLAY_PAUSE.equals(action)) return "playpause";
        return null;
    }

    private void navigate(String rawUrl) {
        if (webView == null) return;
        Uri uri = Uri.parse(rawUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) { status.setText("Secure HTTPS pages only"); return; }
        webView.loadUrl(uri.toString());
    }

    private void performSearch() {
        String query = searchBox == null ? "" : searchBox.getText().toString().trim();
        if (query.isEmpty()) return;
        try { navigate("https://open.spotify.com/search/" + URLEncoder.encode(query, "UTF-8").replace("+", "%20")); }
        catch (UnsupportedEncodingException ignored) { }
    }

    private boolean isSpotifyPlayer(String rawUrl) {
        if (rawUrl == null) return false;
        Uri uri = Uri.parse(rawUrl);
        return "https".equalsIgnoreCase(uri.getScheme()) && "open.spotify.com".equalsIgnoreCase(uri.getHost());
    }

    private int getChromeMajorVersion(String userAgent) {
        if (userAgent == null) return -1;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\bChrome/(\\d+)(?:\\.|\\b)").matcher(userAgent);
        if (!matcher.find()) return -1;
        try { return Integer.parseInt(matcher.group(1)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private String asAndroidChromeUserAgent(String userAgent) {
        if (userAgent == null || !userAgent.contains("Android")) return userAgent;
        return userAgent.replaceFirst(";\\s*wv\\)", ")")
            .replaceFirst("\\sVersion/4\\.0(?=\\s)", "")
            .replaceFirst("\\s+Build/[^;)]*", "");
    }

    private String getWebViewProviderVersion() {
        if (Build.VERSION.SDK_INT >= 26) {
            PackageInfo provider = WebView.getCurrentWebViewPackage();
            if (provider != null) return provider.packageName + " " + provider.versionName;
        }
        return "Not available on this Android version";
    }

    private void showWebViewDiagnostics() {
        String engineVersion = webViewChromeMajor > 0 ? Integer.toString(webViewChromeMajor) : "Unknown";
        String details = "Android API: " + Build.VERSION.SDK_INT +
            "\nWebView provider: " + webViewProvider +
            "\nChromium/Chrome major: " + engineVersion +
            "\nNative Widevine: " + (nativeWidevineSupported ? "available" : "unavailable") +
            "\nUser-Agent:\n" + (webView == null ? "Unavailable" : webView.getSettings().getUserAgentString()) +
            "\n\nWeb page checks:\n" + pagePlaybackDiagnostics;
        TextView text = new TextView(this);
        text.setText(details);
        text.setTextIsSelectable(true);
        text.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this).setTitle("WebView diagnostics").setView(scroll)
            .setPositiveButton("Copy", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Spotifree diagnostics", details));
            })
            .setNegativeButton("Close", null).show();
    }

    private void dispatchControl(String action) {
        if (isSpotifyPlayer(webView == null ? null : webView.getUrl())) performControl(action);
        else { pendingControl = action; navigate(HOME); }
    }

    private void performControl(String action) {
        if (webView == null || !isSpotifyPlayer(webView.getUrl())) { pendingControl = action; return; }
        String script;
        if ("next".equals(action)) script = "(function(){var b=document.querySelector('[data-testid=\"control-button-skip-forward\"]');if(b)b.click();})()";
        else if ("previous".equals(action)) script = "(function(){var b=document.querySelector('[data-testid=\"control-button-skip-back\"]');if(b)b.click();})()";
        else script = "(function(){var b=document.querySelector('[data-testid=\"control-button-playpause\"]');if(b){b.click();return;}var m=document.querySelector('audio,video');if(m){if(m.paused)m.play();else m.pause();}})()";
        webView.evaluateJavascript(script, null);
    }

    private void startPolling() {
        polling = true;
        handler.removeCallbacks(pollTask);
        handler.postDelayed(pollTask, 400L);
    }

    private void stopPolling() { polling = false; handler.removeCallbacks(pollTask); }

    private void pollPlayerState() {
        if (!polling || webView == null || !isSpotifyPlayer(webView.getUrl())) return;
        webView.evaluateJavascript(STATE_SCRIPT, value -> {
            if (webView == null || !polling) return;
            try {
                JSONObject state = new JSONObject(value);
                if (!state.optBoolean("hasMedia", false)) clearMediaIfNeeded();
                else {
                    String title = safe(state.optString("title"), "Spotify Web Player");
                    String artist = safe(state.optString("artist"), "Spotify");
                    publishMediaState(title, artist, state.optBoolean("playing", false),
                        Math.max(0L, state.optLong("position", 0L)), Math.max(0L, state.optLong("duration", 0L)));
                }
            } catch (Exception ignored) { }
            if (polling) handler.postDelayed(pollTask, POLL_MS);
        });
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() || "null".equals(value) ? fallback : value;
    }

    private void publishMediaState(String title, String artist, boolean playing, long position, long duration) {
        String key = title + "|" + artist + "|" + playing;
        long now = android.os.SystemClock.elapsedRealtime();
        if (key.equals(lastMediaKey) && now - lastStateAt < 5000L) return;
        lastMediaKey = key;
        lastStateAt = now;
        lastHadMedia = true;
        Intent intent = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_STATE)
            .putExtra("hasMedia", true).putExtra("title", title).putExtra("artist", artist)
            .putExtra("playing", playing).putExtra("position", position).putExtra("duration", duration);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
    }

    private void clearMediaIfNeeded() {
        if (!lastHadMedia) return;
        lastHadMedia = false;
        lastMediaKey = "";
        lastStateAt = 0L;
        stopService(new Intent(this, PlaybackService.class));
    }

    static void requestControl(Context context, String command) {
        MainActivity activity = active.get();
        if (activity != null) {
            activity.runOnUiThread(() -> activity.dispatchControl(command));
            return;
        }
        String action = "next".equals(command) ? PlaybackService.ACTION_NEXT
            : "previous".equals(command) ? PlaybackService.ACTION_PREVIOUS : PlaybackService.ACTION_PLAY_PAUSE;
        Intent intent = new Intent(context, MainActivity.class).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        stopPolling();
        if (active.get() == this) active = new WeakReference<MainActivity>(null);
        if (isFinishing()) stopService(new Intent(this, PlaybackService.class));
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}

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
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;

public final class MainActivity extends Activity {
    private static final String HOME = "https://open.spotify.com/";
    private static WeakReference<MainActivity> current = new WeakReference<MainActivity>(null);
    private WebView web;
    private EditText search;
    private TextView status;
    private Button adButton;
    private AdBlocker blocker;
    private ProfileStore profiles;
    private volatile boolean blocking;
    private String pendingControl;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        current = new WeakReference<MainActivity>(this);
        blocker = new AdBlocker(this);
        profiles = new ProfileStore(this);
        blocking = getSharedPreferences("settings", 0).getBoolean("adblock", true);
        buildUi();
        configureWebView();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        Intent service = new Intent(this, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        openIntent(getIntent());
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private Button button(String text, LinearLayout row, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setOnClickListener(listener);
        row.addView(button, new LinearLayout.LayoutParams(0, dp(45), 1));
        return button;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.setHint("Search Spotify");
        search.setTextSize(14);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v, actionId, event) -> { doSearch(); return true; });
        top.addView(search, new LinearLayout.LayoutParams(0, dp(45), 3));
        button("⌕", top, v -> doSearch());
        button("⏮", top, v -> control("previous"));
        button("⏯", top, v -> control("playpause"));
        button("⏭", top, v -> control("next"));
        root.addView(top);
        LinearLayout second = new LinearLayout(this);
        button("Home", second, v -> web.loadUrl(HOME));
        button("Reload", second, v -> web.reload());
        button("Profiles", second, v -> showProfiles());
        adButton = button("Ads: on", second, v -> {
            blocking = !blocking;
            getSharedPreferences("settings", 0).edit().putBoolean("adblock", blocking).apply();
            adButton.setText(blocking ? "Ads: on" : "Ads: off");
            Toast.makeText(this, blocking ? "Ad filtering on" : "Ad filtering off", Toast.LENGTH_SHORT).show();
        });
        adButton.setText(blocking ? "Ads: on" : "Ads: off");
        root.addView(second);
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this);
        status.setText("Spotify Web Player");
        status.setTextColor(Color.LTGRAY);
        status.setSingleLine(true);
        status.setPadding(dp(8), dp(3), dp(8), dp(3));
        root.addView(status);
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        java.util.regex.Matcher chrome = java.util.regex.Pattern.compile("Chrome/([0-9.]+)").matcher(settings.getUserAgentString());
        if (chrome.find()) settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome.group(1) + " Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new Bridge(), "SpotifreeBridge");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) { if (progress < 100) status.setText("Loading " + progress + "%"); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) { }
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return blocking ? blocker.intercept(request.getUrl().toString()) : null;
            }
            @Override public void onPageFinished(WebView view, String url) {
                status.setText(view.getTitle() == null ? "Spotify Web Player" : view.getTitle());
                if (isSpotifyPlayer(url)) view.evaluateJavascript(BRIDGE_SCRIPT, value -> {
                    if (pendingControl != null) {
                        String action = pendingControl;
                        pendingControl = null;
                        control(action);
                    }
                });
            }
        });
    }

    private boolean isSpotifyPlayer(String url) {
        try { Uri uri = Uri.parse(url); return "https".equals(uri.getScheme()) && "open.spotify.com".equals(uri.getHost()); }
        catch (Exception ignored) { return false; }
    }

    private static final String BRIDGE_SCRIPT = "(function(){if(window.__spotifreeInstalled)return;window.__spotifreeInstalled=true;" +
        "window.__spotifreeControl=function(a,p){var id=a==='next'?'control-button-skip-forward':a==='previous'?'control-button-skip-back':'control-button-playpause';" +
        "var m=document.querySelector('audio,video');if(a==='seek'&&m){m.currentTime=p/1000;return;}" +
        "if((a==='play'||a==='pause')&&m&&m.paused===(a==='pause'))return;" +
        "var b=document.querySelector('[data-testid=\\\"'+id+'\\\"]');if(b){b.click();return;}" +
        "if(m&&a==='play')m.play();else if(m&&a==='pause')m.pause();else if(m&&a==='playpause'){if(m.paused)m.play();else m.pause();}};" +
        "var last='';setInterval(function(){try{var m=document.querySelector('audio,video');var meta=navigator.mediaSession&&navigator.mediaSession.metadata;" +
        "var title=meta&&meta.title||document.title||'Spotify Web Player';var artist=meta&&meta.artist||'';" +
        "var playing=m?!m.paused:!!(navigator.mediaSession&&navigator.mediaSession.playbackState==='playing');var pos=m&&isFinite(m.currentTime)?Math.round(m.currentTime*1000):0;" +
        "var dur=m&&isFinite(m.duration)?Math.round(m.duration*1000):0;var key=title+'|'+artist+'|'+playing+'|'+Math.floor(pos/5000);" +
        "if(key!==last){last=key;SpotifreeBridge.state(title,artist,playing,pos,dur);}}catch(e){}},1500);})()";

    private final class Bridge {
        @JavascriptInterface public void state(String title, String artist, boolean playing, long position, long duration) {
            runOnUiThread(() -> {
                if (web == null || !isSpotifyPlayer(web.getUrl())) return;
                Intent intent = new Intent(MainActivity.this, PlaybackService.class).setAction(PlaybackService.STATUS);
                intent.putExtra("title", title).putExtra("artist", artist).putExtra("playing", playing)
                    .putExtra("position", position).putExtra("duration", duration);
                startService(intent);
            });
        }
    }

    static void control(String action) {
        MainActivity activity = current.get();
        if (activity != null) activity.runOnUiThread(() -> {
            if (activity.web != null && activity.isSpotifyPlayer(activity.web.getUrl()))
                activity.web.evaluateJavascript("window.__spotifreeControl&&window.__spotifreeControl('" + action + "',0)", null);
        });
    }

    static void seek(long position) {
        MainActivity activity = current.get();
        if (activity != null) activity.runOnUiThread(() -> {
            if (activity.web != null && activity.isSpotifyPlayer(activity.web.getUrl()))
                activity.web.evaluateJavascript("window.__spotifreeControl&&window.__spotifreeControl('seek'," + position + ")", null);
        });
    }

    private void doSearch() {
        String query = search.getText().toString().trim();
        if (!query.isEmpty()) web.loadUrl("https://open.spotify.com/search/" + Uri.encode(query));
    }

    private void showProfiles() {
        String[] options = {"Save current login", "Switch saved profile", "Delete saved profile"};
        new AlertDialog.Builder(this).setTitle("Profiles: " + profiles.active()).setItems(options, (dialog, choice) -> {
            if (choice == 0) saveProfile();
            else if (choice == 1) selectProfile(false);
            else selectProfile(true);
        }).show();
    }

    private void saveProfile() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Profile name");
        new AlertDialog.Builder(this).setTitle("Save current login").setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                try { profiles.save(input.getText().toString().trim()); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); }
                catch (Exception ex) { Toast.makeText(this, "Could not save profile: " + ex.getMessage(), Toast.LENGTH_LONG).show(); }
            }).setNegativeButton("Cancel", null).show();
    }

    private void selectProfile(boolean delete) {
        java.util.List<String> names = profiles.names();
        if (names.isEmpty()) { Toast.makeText(this, "No saved profiles", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle(delete ? "Delete profile" : "Switch profile")
            .setItems(names.toArray(new String[0]), (dialog, index) -> {
                String name = names.get(index);
                if (delete) { profiles.delete(name); Toast.makeText(this, "Deleted " + name, Toast.LENGTH_SHORT).show(); }
                else {
                    try { profiles.switchTo(name, () -> runOnUiThread(() -> {
                        web.loadUrl(HOME);
                        Toast.makeText(this, "Switched to " + name, Toast.LENGTH_SHORT).show();
                    })); }
                    catch (Exception ex) { Toast.makeText(this, "Could not switch: " + ex.getMessage(), Toast.LENGTH_LONG).show(); }
                }
            }).show();
    }

    private void openIntent(Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (PlaybackService.PREVIOUS.equals(action) || PlaybackService.NEXT.equals(action) || PlaybackService.PLAY_PAUSE.equals(action)) {
            String controlAction = PlaybackService.PREVIOUS.equals(action) ? "previous" : PlaybackService.NEXT.equals(action) ? "next" : "playpause";
            if (web != null && isSpotifyPlayer(web.getUrl())) control(controlAction);
            else { pendingControl = controlAction; web.loadUrl(HOME); }
            return;
        }
        Uri data = intent == null ? null : intent.getData();
        web.loadUrl(data != null && isSpotifyPlayer(data.toString()) ? data.toString() : HOME);
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); openIntent(intent); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() {
        current.clear();
        if (web != null) { web.destroy(); web = null; }
        if (isFinishing()) stopService(new Intent(this, PlaybackService.class));
        super.onDestroy();
    }
}

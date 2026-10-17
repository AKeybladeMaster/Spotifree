package com.spotifree.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

public final class PlaybackService extends Service {
    static final String ACTION_STATE = "com.spotifree.v2.STATE";
    static final String ACTION_PREVIOUS = "com.spotifree.v2.PREVIOUS";
    static final String ACTION_PLAY_PAUSE = "com.spotifree.v2.PLAY_PAUSE";
    static final String ACTION_NEXT = "com.spotifree.v2.NEXT";
    private static final String CHANNEL = "spotifree-playback-v2";
    private static final int NOTIFICATION_ID = 47;
    private MediaSession session;
    private String title = "Spotifree";
    private String artist = "Open Spotify to play";
    private boolean playing;
    private boolean foreground;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Spotifree playback", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "Spotifree v2");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { MainActivity.requestControl(PlaybackService.this, "playpause"); }
            @Override public void onPause() { MainActivity.requestControl(PlaybackService.this, "playpause"); }
            @Override public void onSkipToNext() { MainActivity.requestControl(PlaybackService.this, "next"); }
            @Override public void onSkipToPrevious() { MainActivity.requestControl(PlaybackService.this, "previous"); }
        });
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_STATE.equals(intent.getAction())) return START_NOT_STICKY;
        if (!intent.getBooleanExtra("hasMedia", false)) { stopSelf(startId); return START_NOT_STICKY; }
        title = safe(intent.getStringExtra("title"), "Spotify Web Player");
        artist = safe(intent.getStringExtra("artist"), "Spotify");
        playing = intent.getBooleanExtra("playing", false);
        long position = Math.max(0L, intent.getLongExtra("position", 0L));
        long duration = Math.max(0L, intent.getLongExtra("duration", 0L));
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist).putLong(MediaMetadata.METADATA_KEY_DURATION, duration).build());
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, playing ? 1f : 0f).build());
        session.setActive(true);
        Notification notification = buildNotification();
        if (!foreground) { startForeground(NOTIFICATION_ID, notification); foreground = true; }
        else ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification);
        getSharedPreferences("playback", 0).edit().putString("title", title).putString("artist", artist).putBoolean("playing", playing).apply();
        HomeWidget.update(this);
        return START_NOT_STICKY;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() || "null".equals(value) ? fallback : value;
    }

    private PendingIntent activityAction(String action, int code) {
        Intent intent = new Intent(this, MainActivity.class).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title).setContentText(artist).setContentIntent(content).setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", activityAction(ACTION_PREVIOUS, 1))
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play", activityAction(ACTION_PLAY_PAUSE, 2))
            .addAction(android.R.drawable.ic_media_next, "Next", activityAction(ACTION_NEXT, 3))
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2)).build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        if (session != null) { session.setActive(false); session.release(); }
        if (foreground) stopForeground(true);
        getSharedPreferences("playback", 0).edit().clear().apply();
        HomeWidget.update(this);
        super.onDestroy();
    }
}

package com.spotifree.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

public final class PlaybackService extends Service {
    static final String PREVIOUS = "com.spotifree.app.PREVIOUS";
    static final String PLAY_PAUSE = "com.spotifree.app.PLAY_PAUSE";
    static final String NEXT = "com.spotifree.app.NEXT";
    static final String STATUS = "com.spotifree.app.STATUS";
    private static final String CHANNEL = "playback";
    private MediaSession session;
    private String title = "Spotifree";
    private String artist = "Open to play";
    private boolean playing;
    private long position;
    private long duration;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "Spotifree");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { MainActivity.control("play"); }
            @Override public void onPause() { MainActivity.control("pause"); }
            @Override public void onSkipToNext() { MainActivity.control("next"); }
            @Override public void onSkipToPrevious() { MainActivity.control("previous"); }
            @Override public void onSeekTo(long pos) { MainActivity.seek(pos); }
        });
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setActive(true);
        refresh();
        startForeground(47, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (PREVIOUS.equals(action)) MainActivity.control("previous");
        else if (NEXT.equals(action)) MainActivity.control("next");
        else if (PLAY_PAUSE.equals(action)) MainActivity.control("playpause");
        else if (STATUS.equals(action)) {
            title = safe(intent.getStringExtra("title"), "Spotifree");
            artist = safe(intent.getStringExtra("artist"), "Spotify Web Player");
            playing = intent.getBooleanExtra("playing", false);
            position = intent.getLongExtra("position", 0);
            duration = intent.getLongExtra("duration", 0);
            getSharedPreferences("state", 0).edit().putString("title", title).putString("artist", artist).putBoolean("playing", playing).apply();
            refresh();
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(47, notification());
            HomeWidget.update(this);
        }
        return START_STICKY;
    }

    private static String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }

    private void refresh() {
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration).build());
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, playing ? 1f : 0f).build());
    }

    private PendingIntent command(String action, int code) {
        return PendingIntent.getActivity(this, code, new Intent(this, MainActivity.class).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(artist)
            .setContentIntent(content).setOngoing(playing).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", command(PREVIOUS, 1))
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play", command(PLAY_PAUSE, 2))
            .addAction(android.R.drawable.ic_media_next, "Next", command(NEXT, 3))
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return builder.build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { session.release(); super.onDestroy(); }
}

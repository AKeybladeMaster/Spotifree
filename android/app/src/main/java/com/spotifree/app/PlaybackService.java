package com.spotifree.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.IBinder;
import android.os.Build;

public final class PlaybackService extends Service {
    static final String ACTION_STATE = "com.spotifree.app.UPDATE_STATE";
    static final String ACTION_CONTROL = "com.spotifree.app.MEDIA_CONTROL";
    private static final String CHANNEL = "spotifree-playback";
    private static final int ID = 41;

    private MediaSession session;
    private NotificationManager notifications;
    private String title = "Spotifree";
    private String artist = "";
    private boolean playing;
    private boolean foreground;
    private long positionMs;
    private long durationMs;

    @Override public void onCreate() {
        super.onCreate();
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Music playback", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "Spotifree");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { MainActivity.control("play"); }
            @Override public void onPause() { MainActivity.control("pause"); }
            @Override public void onSkipToNext() { MainActivity.control("next"); }
            @Override public void onSkipToPrevious() { MainActivity.control("previous"); }
        });
        session.setActive(true);
        updateSession();
    }

    private PendingIntent command(String control, int code) {
        Intent intent = new Intent(this, PlaybackService.class)
                .setAction(ACTION_CONTROL).putExtra("command", control);
        return PendingIntent.getService(this, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateSession() {
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build());
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                positionMs, playing ? 1.0f : 0.0f)
            .build());
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(this, 7, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action previous = new Notification.Action.Builder(
            android.R.drawable.ic_media_previous, "Previous", command("previous", 1)).build();
        Notification.Action playPause = new Notification.Action.Builder(
            playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
            playing ? "Pause" : "Play", command(playing ? "pause" : "play", 2)).build();
        Notification.Action next = new Notification.Action.Builder(
            android.R.drawable.ic_media_next, "Next", command("next", 3)).build();
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openApp)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(previous)
            .addAction(playPause)
            .addAction(next)
            .setStyle(new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_CONTROL.equals(action)) {
            String control = intent.getStringExtra("command");
            if ("play".equals(control) || "pause".equals(control)
                    || "next".equals(control) || "previous".equals(control)) {
                MainActivity.control(control);
            }
            return START_NOT_STICKY;
        }
        if (!ACTION_STATE.equals(action)) return START_NOT_STICKY;
        title = trim(intent.getStringExtra("title"), "Spotify Web Player");
        artist = trim(intent.getStringExtra("artist"), "");
        playing = intent.getBooleanExtra("playing", false);
        positionMs = Math.max(0, intent.getLongExtra("position", 0));
        durationMs = Math.max(0, intent.getLongExtra("duration", 0));
        updateSession();
        Notification n = notification();
        if (playing) {
            // Required promptly after startForegroundService on Android O+.
            startForeground(ID, n);
            foreground = true;
        } else {
            if (foreground) {
                stopForeground(STOP_FOREGROUND_DETACH);
                foreground = false;
            }
            notifications.notify(ID, n);
        }
        PlayerWidget.renderAll(this, title, playing);
        return START_NOT_STICKY;
    }

    private static String trim(String value, String fallback) {
        if (value == null) return fallback;
        value = value.trim();
        return value.isEmpty() ? fallback : value.substring(0, Math.min(value.length(), 120));
    }

    @Override public void onDestroy() {
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        if (notifications != null) notifications.cancel(ID);
        PlayerWidget.renderAll(this, "Spotifree", false);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

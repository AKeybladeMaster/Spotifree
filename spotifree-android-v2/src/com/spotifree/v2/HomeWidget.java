package com.spotifree.v2;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
public final class HomeWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { update(context); }
    static void update(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, HomeWidget.class));
        String title = context.getSharedPreferences("playback", 0).getString("title", "Spotifree");
        String artist = context.getSharedPreferences("playback", 0).getString("artist", "Open Spotify to play");
        boolean playing = context.getSharedPreferences("playback", 0).getBoolean("playing", false);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
            views.setTextViewText(R.id.track, title); views.setTextViewText(R.id.artist, artist);
            views.setTextViewText(R.id.play, playing ? "Pause" : "Play");
            views.setContentDescription(R.id.previous, "Previous track");
            views.setContentDescription(R.id.play, playing ? "Pause playback" : "Play playback");
            views.setContentDescription(R.id.next, "Next track");
            views.setOnClickPendingIntent(R.id.previous, action(context, PlaybackService.ACTION_PREVIOUS, 1));
            views.setOnClickPendingIntent(R.id.play, action(context, PlaybackService.ACTION_PLAY_PAUSE, 2));
            views.setOnClickPendingIntent(R.id.next, action(context, PlaybackService.ACTION_NEXT, 3));
            manager.updateAppWidget(id, views);
        }
    }
    private static PendingIntent action(Context context, String name, int code) {
        Intent intent = new Intent(context, MainActivity.class).setAction(name)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
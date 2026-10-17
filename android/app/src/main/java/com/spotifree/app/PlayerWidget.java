package com.spotifree.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public final class PlayerWidget extends AppWidgetProvider {
    private static final String ACTION_COMMAND = "com.spotifree.app.WIDGET_COMMAND";

    private static PendingIntent widgetControl(Context context, String command, int code) {
        Intent intent = new Intent(context, PlayerWidget.class)
            .setAction(ACTION_COMMAND)
            .putExtra("command", command);
        return PendingIntent.getBroadcast(context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void renderAll(Context context, String title, boolean playing) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, PlayerWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.player_widget);
            views.setTextViewText(R.id.widget_title,
                title == null || title.isEmpty() ? "Spotifree" : title);
            views.setTextViewText(R.id.widget_toggle, playing ? "Ⅱ" : "▶");
            views.setContentDescription(R.id.widget_previous, "Previous track");
            views.setContentDescription(R.id.widget_toggle, playing ? "Pause" : "Play");
            views.setContentDescription(R.id.widget_next, "Next track");
            views.setOnClickPendingIntent(R.id.widget_previous, widgetControl(context, "previous", 21));
            views.setOnClickPendingIntent(R.id.widget_toggle, widgetControl(context, "toggle", 22));
            views.setOnClickPendingIntent(R.id.widget_next, widgetControl(context, "next", 23));
            PendingIntent open = PendingIntent.getActivity(context, 20,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_title, open);
            manager.updateAppWidget(id, views);
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_COMMAND.equals(intent.getAction())) {
            String command = intent.getStringExtra("command");
            if ("toggle".equals(command) || "next".equals(command)
                    || "previous".equals(command)) {
                MainActivity.control(command);
            }
        }
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        renderAll(context, "Spotifree", false);
    }
}
